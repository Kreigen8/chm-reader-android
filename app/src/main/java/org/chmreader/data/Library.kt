package org.chmreader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class Book(val id: String, val name: String, val size: Long, val opened: Long)
class Library(val context: Context) {
    val preferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    fun file(book: Book) = File(booksDir, "${book.id}.chm")
    @Synchronized fun books(): List<Book> = runCatching {
        val a = JSONArray(preferences.getString("books", "[]")); (0 until a.length()).map { i ->
            a.getJSONObject(i).let { Book(it.getString("id"), it.getString("name"), it.getLong("size"), it.getLong("opened")) }
        }.filter { file(it).exists() }.sortedByDescending { it.opened }
    }.getOrDefault(emptyList())
    @Synchronized fun touch(book: Book) {
        val all = listOf(book.copy(opened = System.currentTimeMillis())) + books().filter { it.id != book.id }
        val a = JSONArray(); all.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("opened", it.opened)) }
        preferences.edit().putString("books", a.toString()).putString("lastBook", book.id).apply()
    }
    fun import(uri: Uri, cancel: AtomicBoolean, progress: (Long, Long) -> Unit): Book {
        var name = "Справочник.chm"; var total = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0) ?: name; if (!c.isNull(1)) total = c.getLong(1) }
        }
        val temporary = File.createTempFile("import-", ".part", booksDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256"); var done = 0L; var lastReport = 0L
            context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { out ->
                val b = ByteArray(256*1024)
                while (true) {
                    if (cancel.get() || Thread.currentThread().isInterrupted) throw CancellationException()
                    val n = input.read(b); if (n < 0) break
                    out.write(b, 0, n); digest.update(b, 0, n); done += n
                    if (done-lastReport >= 2*1024*1024) { progress(done, total); lastReport = done }
                }
            } } ?: error("Не удалось открыть выбранный файл")
            temporary.inputStream().use { val magic=ByteArray(4); require(it.read(magic)==4 && magic.contentEquals("ITSF".toByteArray())) { "Выбранный файл не является CHM" } }
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            val book = Book(id, name, done, System.currentTimeMillis()); val target = file(book)
            if (!target.exists() && !temporary.renameTo(target)) error("Недостаточно места для сохранения справочника")
            return book
        } finally { temporary.delete() }
    }
    fun state(book: Book) = context.getSharedPreferences("book_${book.id}", Context.MODE_PRIVATE)
}
