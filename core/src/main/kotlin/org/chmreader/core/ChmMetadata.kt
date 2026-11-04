package org.chmreader.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

data class ChmMetadata(val title: String, val contents: String?, val home: String?, val charset: Charset)
object SystemMetadata {
    fun parse(bytes: ByteArray, headerLanguage: Int = 0x409): ChmMetadata {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val records = HashMap<Int, ByteArray>()
        if (b.remaining() >= 4) b.int
        while (b.remaining() >= 4) {
            val type = b.short.toInt() and 0xffff; val size = b.short.toInt() and 0xffff
            if (size > b.remaining()) break
            records[type] = ByteArray(size).also { b.get(it) }
        }
        val lcid = records[4]?.takeIf { it.size >= 4 }?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).int } ?: headerLanguage
        val charset = TextEncoding.language(lcid)
        fun field(id: Int) = records[id]?.let { TextEncoding.decode(it.takeWhile { byte -> byte != 0.toByte() }.toByteArray(), charset) }?.takeIf { it.isNotBlank() }
        return ChmMetadata(field(3).orEmpty(), field(0), field(2), charset)
    }
}
