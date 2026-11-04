package org.chmreader.core

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextEncoding {
    private val meta = Regex("charset\\s*=\\s*['\"\\s]*([a-zA-Z0-9._-]+)", RegexOption.IGNORE_CASE)
    fun charset(bytes: ByteArray, fallback: Charset, override: String? = null): Charset {
        if (override != null) return Charset.forName(override)
        if (bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) return Charsets.UTF_8
        if (bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) return Charsets.UTF_16LE
        if (bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) return Charsets.UTF_16BE
        val head = String(bytes, 0, minOf(bytes.size, 16384), Charsets.ISO_8859_1)
        meta.find(head)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull()?.let { cs -> return cs } }
        // End-of-input=false permits a truncated multibyte character in a resource prefix.
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val utf8 = runCatching { val out = java.nio.CharBuffer.allocate(bytes.size + 1); !decoder.decode(ByteBuffer.wrap(bytes), out, false).isError }.getOrDefault(false)
        if (utf8 && bytes.any { it < 0 }) return Charsets.UTF_8
        return fallback
    }
    fun decode(bytes: ByteArray, fallback: Charset, override: String? = null): String = String(bytes, charset(bytes, fallback, override)).trimStart('\uFEFF')
    fun language(lcid: Int): Charset = Charset.forName(when (lcid and 0x3ff) {
        0x19, 0x22, 0x23, 0x2f, 0x3f, 0x40, 0x44, 0x50 -> "windows-1251"
        0x05, 0x0e, 0x15, 0x18, 0x1a, 0x1b, 0x24 -> "windows-1250"
        0x08 -> "windows-1253"
        0x1f -> "windows-1254"
        0x0d -> "windows-1255"
        0x01, 0x20, 0x29 -> "windows-1256"
        0x1e -> "windows-874"
        0x11 -> "Shift_JIS"
        0x12 -> "EUC-KR"
        0x04 -> if (lcid == 0x404 || lcid == 0xc04 || lcid == 0x1404) "Big5" else "GB18030"
        else -> "windows-1252"
    })
}
