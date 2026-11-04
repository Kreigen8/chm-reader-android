package org.chmreader.core

import java.net.URI

data class ReleaseUpdate(val versionCode: Long, val versionName: String, val packageName: String, val apkUrl: String, val sha256: String, val size: Long, val notes: String)

class UpdatePolicy(val repository: String, private val packageName: String) {
    val manifestUrl = "https://github.com/$repository/releases/latest/download/update.json"
    val releasesUrl = "https://github.com/$repository/releases"
    val maxApkBytes = 200L * 1024 * 1024
    fun validate(update: ReleaseUpdate): ReleaseUpdate {
        require(update.packageName == packageName) { "Обновление предназначено для другого приложения" }
        require(update.versionCode in 1..2_100_000_000L && update.versionName.isNotBlank() && update.versionName.length <= 64) { "Некорректная версия обновления" }
        require(update.size in 1..maxApkBytes) { "Недопустимый размер APK" }
        require(Regex("[a-fA-F0-9]{64}").matches(update.sha256)) { "Нет корректной контрольной суммы APK" }
        val uri = URI(update.apkUrl)
        val prefix = "/$repository/releases/download/"
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port in listOf(-1,443) && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.startsWith(prefix)) { "APK должен находиться в GitHub Releases этого проекта" }
        val suffix = uri.rawPath.removePrefix(prefix).split('/')
        require(suffix.size == 2 && suffix[0] !in listOf(".","..") && Regex("[A-Za-z0-9._-]+").matches(suffix[0]) && Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*\\.apk").matches(suffix[1])) { "Некорректный адрес APK" }
        return update
    }
    fun allowNetworkUrl(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && u.port in listOf(-1,443) && (u.host == "github.com" || u.host == "release-assets.githubusercontent.com" || u.host == "objects.githubusercontent.com" || u.host == "github-releases.githubusercontent.com")
    }.getOrDefault(false)
}
