package org.chmreader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.chmreader.chm.ChmDocument
import org.chmreader.chm.NativeChm
import org.chmreader.core.TextEncoding
import org.jsoup.Jsoup
import java.util.concurrent.atomic.AtomicBoolean

data class SearchHit(val title: String, val path: String, val excerpt: String)
data class IndexProgress(val done: Int, val total: Int, val complete: Boolean, val skipped: Int = 0)

class SearchIndex(context: Context, id: String) : SQLiteOpenHelper(context, "search_$id.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE pages USING fts4(title,body,path,notindexed=path,tokenize=unicode61)")
        db.execSQL("CREATE TABLE indexed(path TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY,value INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun progress(total: Int): IndexProgress {
        val db = readableDatabase
        val done = db.rawQuery("SELECT count(*) FROM indexed", null).use { it.moveToFirst(); it.getInt(0) }
        val complete = db.rawQuery("SELECT value FROM state WHERE key='complete'", null).use { it.moveToFirst() && it.getInt(0) == 1 }
        val skipped = db.rawQuery("SELECT value FROM state WHERE key='skipped'", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return IndexProgress(done, total, complete, skipped)
    }
    fun search(query: String): List<SearchHit> {
        val terms = Regex("[\\p{L}\\p{N}_]+").findAll(query).take(12).map { "\"${it.value}\"*" }.toList()
        if (terms.isEmpty()) return emptyList()
        return readableDatabase.rawQuery("SELECT title,path,snippet(pages,'','',' … ',1,28) FROM pages WHERE pages MATCH ? LIMIT 150", arrayOf(terms.joinToString(" AND "))).use { c ->
            buildList { while (c.moveToNext()) add(SearchHit(c.getString(0), c.getString(1), c.getString(2))) }
        }
    }
    fun build(document: ChmDocument, cancel: AtomicBoolean, update: (IndexProgress) -> Unit) {
        val pages = document.archive.entries.filter { Regex("\\.(?:html?|xhtml)$", RegexOption.IGNORE_CASE).containsMatchIn(it.path) }
        val db = writableDatabase
        val indexed = db.rawQuery("SELECT path FROM indexed", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        var done = indexed.size; var skipped = 0
        NativeChm(document.archive.file).use { archive ->
            for (page in pages) {
                if (cancel.get() || Thread.currentThread().isInterrupted) break
                if (page.path in indexed) continue
                try {
                    val bytes = archive.bytes(page.path)!!
                    val html = Jsoup.parse(TextEncoding.decode(bytes, document.metadata.charset))
                    html.select("script,style,noscript").remove()
                    val title = html.title().ifBlank { document.toc.firstOrNull { it.target?.substringBefore('#') == page.path }?.title ?: page.path.substringAfterLast('/') }
                    val body = html.body().text()
                    if (cancel.get()) break
                    db.beginTransaction()
                    try {
                        db.execSQL("INSERT INTO pages(title,body,path) VALUES(?,?,?)", arrayOf(title, body, page.path))
                        db.execSQL("INSERT INTO indexed(path) VALUES(?)", arrayOf(page.path))
                        db.setTransactionSuccessful(); done++
                    } finally { db.endTransaction() }
                } catch (e: java.io.IOException) { skipped++ }
                if (done % 12 == 0 || done + skipped == pages.size) update(IndexProgress(done, pages.size, false, skipped))
            }
        }
        val complete = !cancel.get() && done+skipped == pages.size
        db.execSQL("INSERT OR REPLACE INTO state VALUES('complete',?)", arrayOf(if (complete) 1 else 0))
        db.execSQL("INSERT OR REPLACE INTO state VALUES('skipped',?)", arrayOf(skipped))
        update(IndexProgress(done, pages.size, complete, skipped))
    }
}
