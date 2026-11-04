package org.chmreader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.chmreader.chm.ChmDocument
import org.chmreader.data.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ReaderModel(app: Application) : AndroidViewModel(app) {
    val library = Library(app)
    val document = MutableLiveData<Pair<Book, ChmDocument>?>()
    val busy = MutableLiveData<String?>(null)
    val error = MutableLiveData<String?>(null)
    val indexProgress = MutableLiveData<IndexProgress?>(null)
    val indexing = MutableLiveData(false)
    private val io = Executors.newSingleThreadExecutor()
    private val indexExecutor = Executors.newSingleThreadExecutor()
    private val importCancel = AtomicBoolean()
    private var indexCancel = AtomicBoolean()
    @Volatile private var index: SearchIndex? = null
    @Volatile private var generation = 0
    private val indices=java.util.concurrent.CopyOnWriteArrayList<SearchIndex>()
    var searchQuery = ""
    fun restoreLast() {
        if (document.value != null || busy.value != null) return
        library.books().firstOrNull { it.id == library.preferences.getString("lastBook", null) }?.let(::open)
    }
    fun import(uri: Uri) {
        if (busy.value != null) return
        importCancel.set(false); busy.value = "Копирование справочника…"
        io.execute {
            try {
                val book = library.import(uri, importCancel) { done, total ->
                    busy.postValue(if (total > 0) "Копирование: ${done*100/total}%" else "Скопировано ${done/1024/1024} МБ")
                }
                if (!importCancel.get()) load(book)
            } catch (_: java.util.concurrent.CancellationException) { }
            catch (e: Exception) { error.postValue(e.message ?: "Не удалось открыть CHM") }
            finally { busy.postValue(null) }
        }
    }
    fun cancelImport() { importCancel.set(true) }
    fun open(book: Book) {
        if (busy.value != null) return
        busy.value = "Чтение оглавления…"
        io.execute {
            try { load(book) } catch (e: Exception) { error.postValue(e.message ?: "Не удалось открыть CHM") }
            finally { busy.postValue(null) }
        }
    }
    private fun load(book: Book) {
        val doc = ChmDocument.open(library.file(book), book.name)
        library.touch(book)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            indexCancel.set(true); generation++
            document.value?.second?.close()
            document.value = book to doc
            index = SearchIndex(getApplication(), book.id).also { indices.add(it) }
            indexProgress.value = index!!.progress(doc.archive.entries.count { Regex("\\.(?:html?|xhtml)$", RegexOption.IGNORE_CASE).containsMatchIn(it.path) })
            indexing.value = false
        }
    }
    fun startIndex() {
        val doc = document.value?.second ?: return
        val idx = index ?: return
        if (indexing.value == true) return
        val version = generation; val cancel = AtomicBoolean(); indexCancel = cancel; indexing.value = true
        indexExecutor.execute {
            try {
                idx.build(doc, cancel) { p -> if (generation == version) indexProgress.postValue(p) }
            } catch (e: Exception) { if (generation == version) error.postValue("Ошибка индексации: ${e.message}") }
            finally { if (generation == version) indexing.postValue(false) }
        }
    }
    fun cancelIndex() { indexCancel.set(true) }
    fun search(query: String, callback: (List<SearchHit>) -> Unit) {
        val idx = index ?: return
        io.execute {
            val hits = runCatching { idx.search(query) }.getOrElse { error.postValue("Ошибка поиска: ${it.message}"); emptyList() }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(hits) }
        }
    }
    override fun onCleared() {
        importCancel.set(true); indexCancel.set(true)
        io.shutdownNow(); indexExecutor.shutdownNow(); document.value?.second?.close()
        // Each index job owns its database until the executor finishes; never close a live cursor.
        Thread { runCatching {
            io.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS)
            indexExecutor.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS)
            indices.forEach { it.close() }
        } }.start()
    }
}
