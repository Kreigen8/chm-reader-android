package org.chmreader.chm

import org.chmreader.core.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

data class ChmEntry(val path: String, val rawPath: ByteArray, val length: Long)

class NativeChm(val file: File) : Closeable {
    companion object { init { System.loadLibrary("chmbridge") } }
    private var handle = openNative(file.absolutePath)
    private external fun openNative(path: String): Long
    private external fun closeNative(handle: Long)
    private external fun listNative(handle: Long): ByteArray
    private external fun readNative(handle: Long, path: ByteArray, offset: Long, count: Int): ByteArray
    val headerLanguage: Int = RandomAccessFile(file, "r").use { it.seek(20); Integer.reverseBytes(it.readInt()) }
    val entries: List<ChmEntry> = run {
        try {
            val b = ByteBuffer.wrap(listNative(handle)).order(ByteOrder.LITTLE_ENDIAN)
            val result = ArrayList<ChmEntry>(); val fallback = TextEncoding.language(headerLanguage)
            while (b.remaining() >= 12) {
                val length = b.long; val n = b.int
                require(length >= 0 && n in 1..minOf(4096, b.remaining()))
                val raw = ByteArray(n).also { b.get(it) }
                val path = TextEncoding.decode(raw, fallback).replace('\\', '/')
                result.add(ChmEntry(path, raw, length))
            }
            result
        } catch (e: Exception) { close(); throw e }
    }
    private val lookup = entries.associateBy { ArchivePath.key(it.path) }
    fun entry(path: String): ChmEntry? = lookup[ArchivePath.key(path)]
    @Synchronized private fun chunk(entry: ChmEntry, offset: Long, count: Int): ByteArray {
        if (handle == 0L) throw IOException("Справочник закрыт")
        return readNative(handle, entry.rawPath, offset, count)
    }
    fun prefix(entry: ChmEntry, count: Int = 16384) = chunk(entry, 0, minOf(entry.length, count.toLong()).toInt())
    fun bytes(path: String, limit: Int = 32 * 1024 * 1024): ByteArray? {
        val e = entry(path) ?: return null
        if (e.length > limit) throw IOException("Ресурс $path превышает ${limit / 1024 / 1024} МБ")
        return stream(e).use { it.readBytes() }
    }
    fun stream(entry: ChmEntry): InputStream = object : InputStream() {
        var position = 0L; var buffer = ByteArray(0); var cursor = 0; var closed = false
        override fun read(): Int {
            val one = ByteArray(1); return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 255
        }
        override fun read(out: ByteArray, off: Int, len: Int): Int {
            if (closed) throw IOException("Поток закрыт")
            if (off < 0 || len < 0 || off > out.size - len) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            if (position >= entry.length) return -1
            if (cursor >= buffer.size) {
                buffer = chunk(entry, position, minOf(65536L, entry.length-position).toInt()); cursor = 0
                if (buffer.isEmpty()) throw IOException("CHM неожиданно закончился")
            }
            val count = minOf(len, buffer.size-cursor)
            buffer.copyInto(out, off, cursor, cursor+count); cursor += count; position += count
            return count
        }
        override fun available(): Int = minOf(Int.MAX_VALUE.toLong(), entry.length-position).toInt()
        override fun close() { closed = true; buffer = ByteArray(0) }
    }
    @Synchronized override fun close() { if (handle != 0L) { closeNative(handle); handle = 0L } }
}

data class ChmDocument(val archive: NativeChm, val name: String, val metadata: ChmMetadata, val toc: List<TocEntry>, val home: String?, val warning: String?, val tocSource: String) : Closeable {
    override fun close() = archive.close()
    companion object {
        fun open(file: File, name: String): ChmDocument {
            val archive = NativeChm(file)
            try {
                val meta = SystemMetadata.parse(archive.bytes("/#SYSTEM", 4*1024*1024) ?: ByteArray(0), archive.headerLanguage)
                val warnings = ArrayList<String>()
                var source = "HTML .hhc"
                val hhc = meta.contents?.let { ArchivePath.resolve(it, "/", name)?.path }?.let { archive.entry(it) }
                    ?: archive.entries.firstOrNull { it.path.endsWith(".hhc", true) }
                var toc = if (hhc != null) runCatching {
                    HhcParser.parse(TextEncoding.decode(archive.bytes(hhc.path)!!, meta.charset), hhc.path, name)
                }.getOrElse { warnings.add("Не удалось прочитать .hhc: ${it.message}"); emptyList() } else emptyList()
                if (toc.isEmpty() && archive.entry("/#TOCIDX") != null) {
                    source = "Бинарное #TOCIDX"
                    toc = runCatching {
                        BinaryTocParser.parse(archive.bytes("/#TOCIDX")!!, archive.bytes("/#TOPICS")!!, archive.bytes("/#STRINGS")!!, archive.bytes("/#URLTBL")!!, archive.bytes("/#URLSTR")!!, meta.charset, name)
                    }.getOrElse { warnings.add("Повреждено бинарное оглавление: ${it.message}"); emptyList() }
                }
                if (toc.isEmpty()) warnings.add("В справочнике нет доступного оглавления. Страницы доступны через полнотекстовый поиск.")
                val home = meta.home?.let { ArchivePath.resolve(it, "/", name) }?.takeIf { archive.entry(it.path) != null }?.reference
                    ?: toc.firstOrNull { it.target?.let { t -> archive.entry(t.substringBefore('#').substringBefore('?')) != null } == true }?.target
                    ?: archive.entries.firstOrNull { Regex("\\.(?:html?|xhtml)$", RegexOption.IGNORE_CASE).containsMatchIn(it.path) }?.path
                return ChmDocument(archive, name, meta, toc, home, warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"), source)
            } catch (e: Exception) { archive.close(); throw e }
        }
    }
}
