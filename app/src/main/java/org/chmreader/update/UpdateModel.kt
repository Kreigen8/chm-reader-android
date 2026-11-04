package org.chmreader.update

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.chmreader.BuildConfig
import org.chmreader.core.ReleaseUpdate
import org.chmreader.core.UpdatePolicy
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateState(val status: String = "Проверка выполняется только по нажатию кнопки.", val release: ReleaseUpdate? = null, val busy: Boolean = false, val progress: Int? = null, val apk: File? = null)

class UpdateModel(app: Application) : AndroidViewModel(app) {
    val state = MutableLiveData(UpdateState())
    val policy = UpdatePolicy(BuildConfig.UPDATE_REPOSITORY, BuildConfig.APPLICATION_ID)
    private val executor = Executors.newSingleThreadExecutor()
    private var cancel = AtomicBoolean()
    private fun ensureActive(token: AtomicBoolean) { if(token.get() || Thread.currentThread().isInterrupted) throw CancellationException() }
    private fun connect(url: String, token: AtomicBoolean): HttpURLConnection {
        var address = url
        repeat(6) {
            ensureActive(token)
            if(!policy.allowNetworkUrl(address)) throw IOException("Недопустимый сервер обновлений")
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "CHM-Reader/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/octet-stream")
            val code = try { connection.responseCode } catch(e: Exception) { connection.disconnect(); throw e }
            if(code in listOf(301,302,303,307,308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if(location == null) throw IOException("Пустое перенаправление сервера")
                address = URL(URL(address),location).toString()
            } else if(code == 200) return connection
            else { connection.disconnect(); throw IOException(if(code==404) "Обновления пока не опубликованы или недоступны" else "GitHub вернул ошибку $code") }
        }
        throw IOException("Слишком много перенаправлений")
    }
    fun check() {
        if(state.value?.busy == true) return
        val token = AtomicBoolean().also { cancel=it }
        state.value=UpdateState("Проверка версии на GitHub…",busy=true)
        executor.execute {
            try {
                val connection=connect(policy.manifestUrl,token)
                val bytes=try { connection.inputStream.use { input ->
                    val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(4096)
                    while(true) { ensureActive(token); val n=input.read(buffer); if(n<0) break; if(out.size()+n>65536) throw IOException("Слишком большой файл описания обновления"); out.write(buffer,0,n) }
                    out.toByteArray()
                } } finally { connection.disconnect() }
                val json=JSONObject(String(bytes,Charsets.UTF_8))
                val release=policy.validate(ReleaseUpdate(json.getLong("versionCode"),json.getString("versionName"),json.getString("packageName"),json.getString("apkUrl"),json.getString("sha256"),json.getLong("size"),json.optString("notes").take(12000)))
                ensureActive(token)
                state.postValue(if(release.versionCode>BuildConfig.VERSION_CODE) UpdateState("Доступна версия ${release.versionName}",release) else UpdateState("Установлена актуальная версия ${BuildConfig.VERSION_NAME}"))
            } catch(_: CancellationException) { state.postValue(UpdateState("Проверка отменена")) }
            catch(e: Exception) { state.postValue(UpdateState("Не удалось проверить обновление: ${e.message}. Проверьте интернет и повторите попытку.")) }
        }
    }
    fun download() {
        val previous=state.value ?: return
        val release=previous.release ?: return
        if(previous.busy || release.versionCode<=BuildConfig.VERSION_CODE) return
        val token=AtomicBoolean().also { cancel=it }
        state.value=previous.copy(status="Загрузка обновления…",busy=true,progress=0,apk=null)
        executor.execute {
            val dir=File(getApplication<Application>().cacheDir,"updates").apply { mkdirs() }
            val temporary=File(dir,"update.part"); val apk=File(dir,"CHM-Reader-${release.versionCode}.apk")
            try {
                val digest=MessageDigest.getInstance("SHA-256"); var total=0L; var lastPercent=-1
                val connection=connect(release.apkUrl,token)
                try { connection.inputStream.use { input -> temporary.outputStream().use { out ->
                    val buffer=ByteArray(65536)
                    while(true) {
                        ensureActive(token); val n=input.read(buffer); if(n<0) break
                        total+=n; if(total>release.size || total>policy.maxApkBytes) throw IOException("Размер APK не соответствует описанию")
                        out.write(buffer,0,n); digest.update(buffer,0,n)
                        val percent=(total*100/release.size).toInt()
                        if(percent!=lastPercent) { lastPercent=percent; state.postValue(previous.copy(status="Загрузка: $percent%",busy=true,progress=percent,apk=null)) }
                    }
                } } } finally { connection.disconnect() }
                ensureActive(token)
                val actual=digest.digest().joinToString("") { "%02x".format(it) }
                if(total!=release.size || !actual.equals(release.sha256,true)) throw IOException("Контрольная сумма APK не совпадает")
                verifyApk(temporary,release)
                ensureActive(token)
                if(apk.exists()) apk.delete()
                if(!temporary.renameTo(apk)) throw IOException("Не удалось сохранить APK")
                state.postValue(previous.copy(status="APK проверен. Можно установить обновление.",apk=apk,progress=null,busy=false))
            } catch(_: CancellationException) { state.postValue(previous.copy(status="Загрузка отменена",busy=false,progress=null,apk=null)) }
            catch(e: Exception) { state.postValue(previous.copy(status="Не удалось загрузить обновление: ${e.message}",busy=false,progress=null,apk=null)) }
            finally { temporary.delete() }
        }
    }
    @Suppress("DEPRECATION")
    private fun verifyApk(file: File, release: ReleaseUpdate) {
        val pm=getApplication<Application>().packageManager
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate=pm.getPackageArchiveInfo(file.absolutePath,flags) ?: throw IOException("Файл не является Android-приложением")
        val installed=pm.getPackageInfo(BuildConfig.APPLICATION_ID,flags)
        val version=if(Build.VERSION.SDK_INT>=28) candidate.longVersionCode else candidate.versionCode.toLong()
        if(candidate.packageName!=BuildConfig.APPLICATION_ID || version!=release.versionCode || version<=BuildConfig.VERSION_CODE) throw IOException("Неверное приложение или версия APK")
        fun signatures(info:PackageInfo):Set<String> {
            val signatures=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        val trusted=signatures(installed)
        if(trusted.isEmpty() || signatures(candidate)!=trusted) throw IOException("Подпись APK отличается от установленного приложения")
    }
    fun cancel() { cancel.set(true) }
    fun discardMissingApk() { state.value=state.value?.copy(status="Файл обновления удалён из кэша. Загрузите его повторно.",apk=null) }
    override fun onCleared() { cancel(); executor.shutdownNow() }
}
