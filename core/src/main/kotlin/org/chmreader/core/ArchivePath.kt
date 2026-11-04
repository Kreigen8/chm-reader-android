package org.chmreader.core

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

data class TopicPath(val path: String, val query: String = "", val fragment: String = "") {
    val reference: String get() = path + query + fragment
}

/** All paths are archive-rooted; no filesystem path is ever constructed from a topic. */
object ArchivePath {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    fun resolve(reference: String, base: String = "/", archiveName: String? = null): TopicPath? {
        var ref = reference.trim().replace('\\', '/')
        if (ref.any { it.code < 32 } || ref.startsWith("//")) return null
        if (ref.startsWith("mk:@MSITStore:", true)) ref = ref.substring(14)
        else if (ref.startsWith("ms-its:", true)) ref = ref.substring(7)
        else if (ref.startsWith("its:", true)) ref = ref.substring(4)
        if ("::" in ref) {
            val named = ref.substringBefore("::").substringAfterLast('/')
            if (named.isNotBlank() && (archiveName == null || !named.equals(archiveName, true))) return null
            ref = ref.substringAfter("::")
        }
        if (scheme.containsMatchIn(ref) || ref.startsWith("//")) return null
        val fragment = if ('#' in ref) "#" + ref.substringAfter('#') else ""
        ref = ref.substringBefore('#')
        val query = if ('?' in ref) "?" + ref.substringAfter('?') else ""
        ref = ref.substringBefore('?')
        val decoded = try { URLDecoder.decode(ref.replace("+", "%2B"), "UTF-8").replace('\\', '/') } catch (_: Exception) { return null }
        // Do not permit another decoding pass to change traversal or separators.
        if (Regex("%(?:2e|2f|5c|00)", RegexOption.IGNORE_CASE).containsMatchIn(decoded) || decoded.any { it.code < 32 } || ':' in decoded || decoded.startsWith("//")) return null
        val parts = ArrayDeque<String>()
        val basePath = base.substringBefore('#').substringBefore('?')
        val combined = when {
            decoded.startsWith('/') -> decoded
            decoded.isEmpty() -> basePath
            else -> basePath.substringBeforeLast('/', "") + "/" + decoded
        }
        for (part in combined.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> parts.addLast(part)
        }
        return TopicPath("/" + parts.joinToString("/"), query, fragment)
    }
    fun key(path: String) = path.lowercase(Locale.ROOT)
    fun url(host: String, path: TopicPath): String = URI("https", host, path.path, null, null).toASCIIString() + path.query + path.fragment
}
