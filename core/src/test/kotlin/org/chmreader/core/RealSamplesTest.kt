package org.chmreader.core

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.jsoup.Jsoup
import java.io.File

class RealSamplesTest {
    @Test fun privateCompatibilitySamples() {
        val directory=File("../private-tests")
        val samples=directory.listFiles()?.filter { it.isDirectory && File(it,"system.bin").exists() }.orEmpty()
        assumeTrue("Run tools/verify-samples.py with private CHM inputs first",samples.isNotEmpty())
        for(sample in samples) {
            val name=File(sample,"archive-name.txt").readText()
            val language=File(sample,"header-language.txt").readText().toInt()
            val metadata=SystemMetadata.parse(File(sample,"system.bin").readBytes(),language)
            val entries=File(sample,"entries.txt").readLines().map { ArchivePath.key(it) }.toHashSet()
            val tocs=sample.listFiles()!!.filter { it.name.startsWith("toc-") && it.extension=="bin" }
            var all=emptyList<TocEntry>()
            for(toc in tocs) {
                val source=File(sample,toc.nameWithoutExtension+".path").readText()
                val text=TextEncoding.decode(toc.readBytes(),metadata.charset)
                val parsed=HhcParser.parse(text,source,name)
                assertTrue("$name must have many sections",parsed.size>20)
                assertTrue("$name must retain hierarchy",parsed.any { it.level>0 })
                assertTrue("No replacement characters in $name",parsed.none { '\uFFFD' in it.title })
                val valid=parsed.count { it.target?.let { p -> ArchivePath.key(p.substringBefore('#').substringBefore('?')) in entries }==true }
                assertTrue("$name TOC targets resolve ($valid/${parsed.size})",valid>20)
                assertEquals("Every internal TOC target resolves in $name",parsed.count { it.target!=null },valid)
                all=parsed
                println("$name: ${parsed.size} TOC nodes; depth=${parsed.maxOf { it.level }+1}; $valid targets resolve; encoding=${metadata.charset}")
            }
            if(name.equals("CombiLdr.chm",true)) assertTrue(all.any { Regex("[А-Яа-яЁё]").containsMatchIn(it.title) })
            var images=0; var links=0; var missingImages=0
            for(page in sample.listFiles()!!.filter { it.name.startsWith("page-") && it.extension=="bin" }) {
                val path=File(sample,page.nameWithoutExtension+".path").readText()
                val html=Jsoup.parse(TextEncoding.decode(page.readBytes(),metadata.charset))
                for(img in html.select("img[src]")) {
                    val p=ArchivePath.resolve(img.attr("src"),path,name) ?: continue
                    if(ArchivePath.key(p.path) in entries) images++ else missingImages++
                }
                for(link in html.select("a[href]")) {
                    val p=ArchivePath.resolve(link.attr("href"),path,name) ?: continue
                    if(ArchivePath.key(p.path) in entries) links++
                }
            }
            assertTrue("$name internal images",images>0)
            assertTrue("$name internal links",links>0)
            println("$name: $images image references and $links internal links resolve; $missingImages image references absent in source archive")
        }
    }
}
