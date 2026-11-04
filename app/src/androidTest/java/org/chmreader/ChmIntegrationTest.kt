package org.chmreader

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import org.chmreader.chm.*
import org.chmreader.core.*
import org.chmreader.data.*
import org.chmreader.ui.DocumentWebClient
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ChmIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun sample():File {
        val name=InstrumentationRegistry.getArguments().getString("sample") ?: "CombiLdr.chm"
        val file=File(context.filesDir,"test-input/$name")
        check(file.exists()) { "Push the private compatibility sample with tools/test-device.py first: $file" }
        return file
    }
    @Test fun realArchiveTocResourcesAndLinks() {
        val file=sample()
        ChmDocument.open(file,file.name).use { doc ->
            assertTrue("Expected a real tree",doc.toc.size>20)
            assertTrue("Expected nested sections",doc.toc.any { it.level>0 })
            assertNotNull("Home page",doc.home)
            val hasCyrillic=doc.toc.any { Regex("[А-Яа-яЁё]").containsMatchIn(it.title) }
            var pages=0; var images=0; var missingImages=0; var links=0
            val targets=doc.toc.mapNotNull { it.target }.distinct().take(150)
            for(target in targets) {
                val path=ArchivePath.resolve(target,"/",doc.name) ?: continue
                val entry=doc.archive.entry(path.path) ?: continue
                if(entry.length>8*1024*1024) continue
                val bytes=doc.archive.bytes(entry.path)!!
                val html=Jsoup.parse(TextEncoding.decode(bytes,doc.metadata.charset)); pages++
                assertFalse("Original page data",bytes.isEmpty())
                for(img in html.select("img[src]")) {
                    val p=ArchivePath.resolve(img.attr("src"),entry.path,doc.name) ?: continue
                    val resource=doc.archive.entry(p.path)
                    if(resource!=null) { assertTrue(doc.archive.prefix(resource,32).isNotEmpty()); images++ } else missingImages++
                }
                for(a in html.select("a[href]")) {
                    val p=ArchivePath.resolve(a.attr("href"),entry.path,doc.name) ?: continue
                    if(doc.archive.entry(p.path)!=null) links++
                }
            }
            assertTrue("HTML pages decoded",pages>5)
            assertTrue("Image bytes retrieved",images>0)
            assertTrue("Relative internal links resolve",links>0)
            if(file.name.equals("CombiLdr.chm",true)) assertTrue("Cyrillic titles must be decoded",hasCyrillic)
            Log.i("CHM_TEST","${file.name}: size=${file.length()}, toc=${doc.toc.size}, depth=${doc.toc.maxOf { it.level }}, source=${doc.tocSource}, charset=${doc.metadata.charset}, pages=$pages, images=$images, missingSourceImages=$missingImages, links=$links, cyrillic=$hasCyrillic")
        }
    }
    @Test fun fullTextIndexCancellationResumeAndCyrillic() {
        val file=sample()
        ChmDocument.open(file,file.name).use { doc ->
            val id="integration-${file.name}"
            context.deleteDatabase("search_$id.db")
            SearchIndex(context,id).use { idx ->
                val cancel=AtomicBoolean()
                idx.build(doc,cancel) { p -> if(p.done>=24) cancel.set(true) }
                assertTrue(idx.progress(0).done>0)
                assertFalse(idx.progress(0).complete)
                // Large sample only needs incremental verification; resume completion on smaller manual.
                if(file.length()<50*1024*1024) {
                    idx.build(doc,AtomicBoolean()) { }
                    assertTrue(idx.progress(0).complete)
                    assertEquals(0,idx.progress(0).skipped)
                    val word=if(doc.toc.any { it.title.contains("загруз",true) }) "загруз" else "модуль"
                    val hits=idx.search(word)
                    assertTrue("Cyrillic full-text results for $word",hits.isNotEmpty())
                    assertTrue(hits.all { doc.archive.entry(it.path)!=null })
                    Log.i("CHM_TEST","FTS ${file.name}: pages=${idx.progress(0).done}, '$word' hits=${hits.size}")
                }
            }
        }
    }
    @Test fun uiOpensRealManualAndRestoresTree() {
        val source=sample(); val library=Library(context)
        val book=Book("test-${source.nameWithoutExtension.lowercase()}",source.name,source.length(),System.currentTimeMillis())
        if(!library.file(book).exists()) java.nio.file.Files.createLink(library.file(book).toPath(),source.toPath())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val deadline=System.currentTimeMillis()+60000
            var ready=false
            while(System.currentTimeMillis()<deadline) {
                scenario.onActivity {
                    val model=ViewModelProvider(it)[ReaderModel::class.java]
                    if(model.busy.value==null && model.document.value?.first?.id!=book.id) model.open(book)
                    val web=findWeb(it.window.decorView)
                    ready=model.document.value?.first?.id==book.id && web?.url?.startsWith("https://book-")==true && web.progress==100
                }
                if(ready) break
                Thread.sleep(200)
            }
            assertTrue("Reader opened sample",ready)
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[ReaderModel::class.java]
                assertEquals(book.id,model.document.value?.first?.id)
                val web=findWeb(activity.window.decorView)!!
                assertFalse(web.settings.javaScriptEnabled)
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
                assertTrue(web.url?.startsWith("https://book-")==true)
            }
        }
    }
    private fun findWeb(view:android.view.View):android.webkit.WebView? {
        if(view is android.webkit.WebView) return view
        if(view is android.view.ViewGroup) for(i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
}
