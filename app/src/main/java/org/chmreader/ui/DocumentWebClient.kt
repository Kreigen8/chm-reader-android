package org.chmreader.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import org.chmreader.chm.ChmDocument
import org.chmreader.core.ArchivePath
import org.chmreader.core.TextEncoding
import java.io.ByteArrayInputStream
import java.util.Locale

/** A sealed, per-document HTTPS origin. Every request gets a local response or a denial. */
class DocumentWebClient(val document: ChmDocument, val host: String, private val overrideEncoding: () -> String?, private val navigate: (String) -> Unit, private val external: (Uri) -> Unit, private val page: (String) -> Unit, private val finished: () -> Unit) : WebViewClient() {
    fun internal(uri: Uri) = uri.scheme == "https" && uri.host == host && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri=request.url
        if(internal(uri)) return false
        val scheme=uri.scheme?.lowercase(Locale.ROOT)
        if(scheme in setOf("mk", "ms-its", "its") || "::" in uri.toString()) {
            val target=ArchivePath.resolve(uri.toString(),view.url?.let { Uri.parse(it).encodedPath } ?: "/",document.name)
            if(target!=null && request.isForMainFrame) navigate(target.reference)
            return true
        }
        if(request.isForMainFrame && request.hasGesture() && scheme in setOf("http","https","mailto","tel")) external(uri)
        return true
    }
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        if(!internal(request.url) || request.method != "GET") return error(403,"Доступ заблокирован")
        val path=ArchivePath.resolve(request.url.encodedPath ?: "/") ?: return error(403,"Недопустимый путь")
        val entry=document.archive.entry(path.path) ?: return error(404,"Страница или ресурс отсутствует в справочнике: ${path.path}")
        return try {
            val ext=entry.path.substringAfterLast('.').lowercase(Locale.ROOT)
            val mime=when(ext) {
                "htm","html","xhtml" -> "text/html"
                "css" -> "text/css"
                "svg" -> "image/svg+xml"
                "js" -> "application/javascript"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
            val encoding=if(mime.startsWith("text/") || ext=="svg") TextEncoding.charset(document.archive.prefix(entry),document.metadata.charset,overrideEncoding()).name() else null
            WebResourceResponse(mime,encoding,200,"OK",mapOf(
                "Content-Security-Policy" to "default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; frame-src 'self'; media-src 'self'; script-src 'none'; connect-src 'none'; object-src 'none'; form-action 'none'; base-uri 'self'; sandbox allow-same-origin",
                "X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store", "Referrer-Policy" to "no-referrer"
            ),document.archive.stream(entry))
        } catch(e: Exception) { error(500,"Не удалось прочитать ресурс: ${e.message}") }
    }
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        val u=Uri.parse(url)
        if(internal(u)) {
            ArchivePath.resolve((u.encodedPath ?: "/") + (u.encodedQuery?.let { "?$it" } ?: "") + (u.encodedFragment?.let { "#$it" } ?: ""))?.let { page(it.reference) }
        }
    }
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        val u=Uri.parse(url)
        if(internal(u)) ArchivePath.resolve((u.encodedPath ?: "/")+(u.encodedFragment?.let { "#$it" } ?: ""))?.let { page(it.reference) }
    }
    override fun onPageFinished(view: WebView, url: String) { finished() }
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) { handler.cancel() }
    private fun error(code: Int, message: String): WebResourceResponse {
        val safe=org.jsoup.nodes.Entities.escape(message)
        val html="<!doctype html><meta charset=utf-8><title>CHM Reader</title><body style='font:16px sans-serif;padding:24px;color:#17201c;background:white'><h2>Не удалось открыть ресурс</h2><p>$safe</p><p>Вернитесь к оглавлению и выберите другой раздел.</p>"
        return WebResourceResponse("text/html","UTF-8",code,if(code==404) "Not Found" else "Blocked",mapOf("Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'"),ByteArrayInputStream(html.toByteArray()))
    }
}
