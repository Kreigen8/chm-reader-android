package org.chmreader.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

class ReaderCoreTest {
    private val cp1251=Charset.forName("windows-1251")
    private fun obj(name:String,local:String?=null)="<LI><OBJECT type='text/sitemap'><PARAM name='Name' value='$name'>"+(local?.let { "<PARAM name='Local' value='$it'>" } ?: "")+"</OBJECT>"
    @Test fun nestedSitemapAndUnclosedLi() {
        val toc=HhcParser.parse("<HTML><BODY><UL>"+obj("Двигатель")+"<UL>"+obj("Питание","power.htm")+"<UL>"+obj("Схема","images.htm#pin")+"</UL>"+obj("Проверка","check.htm")+"</UL>"+obj("Кузов","body.htm")+"</UL></BODY>","/help/index.hhc","manual.chm")
        assertEquals(listOf("Двигатель","Питание","Схема","Проверка","Кузов"),toc.map { it.title })
        assertEquals(listOf(null,0,1,0,null),toc.map { it.parent })
        assertEquals(listOf(0,1,2,1,0),toc.map { it.level })
        assertEquals("/help/images.htm#pin",toc[2].target)
        assertEquals(listOf(0,4),TocTree.visible(toc,emptySet()))
        assertEquals(listOf(0,1,3,4),TocTree.visible(toc,setOf("h0")))
        assertEquals(listOf("h1","h0"),TocTree.ancestors(toc,2))
    }
    @Test fun sitemapPropertiesEntitiesAndMixedCase() {
        val html="<object type='text/site properties'><param name='Name' value='Ignore'></object><uL><li><ObJeCt TyPe='text/sitemap'><PaRaM NaMe='Name' VaLuE='A &amp; Б'><param name='Local' value='Страница 1.htm?mode=a&amp;b=2#якорь'></object></ul>"
        val toc=HhcParser.parse(html,"/toc.hhc","manual.chm")
        assertEquals(1,toc.size); assertEquals("A & Б",toc[0].title)
        assertEquals("/Страница 1.htm?mode=a&b=2#якорь",toc[0].target)
    }
    @Test fun deepTreeDoesNotRecurse() {
        val count=1000
        val html=buildString { repeat(count) { append("<ul>"); append(obj("Уровень $it","$it.htm")) }; repeat(count) { append("</ul>") } }
        val toc=HhcParser.parse(html,"/toc.hhc","manual.chm")
        assertEquals(count,toc.size); assertEquals(999,toc.last().level)
        assertEquals(count,TocTree.visible(toc,toc.map { it.id }.toSet()).size)
    }
    @Test fun windowsCyrillicAndUtf8() {
        val html="<meta charset=windows-1251><ul>"+obj("Электрические схемы","схемы.htm")+"</ul>"
        assertEquals(html,TextEncoding.decode(html.toByteArray(cp1251),Charsets.UTF_8))
        val utf="<ul>"+obj("Справочник","main.htm")+"</ul>"
        assertEquals(utf,TextEncoding.decode(utf.toByteArray(),cp1251))
    }
    @Test fun bomAndLegacyEncodings() {
        val value="Руководство: проверка датчика"
        for(name in listOf("IBM866","KOI8-R","windows-1251")) {
            val cs=Charset.forName(name); assertEquals(value,TextEncoding.decode(value.toByteArray(cs),cs))
        }
        assertEquals(value,TextEncoding.decode(byteArrayOf(0xff.toByte(),0xfe.toByte())+value.toByteArray(Charsets.UTF_16LE),cp1251))
        assertEquals(value,TextEncoding.decode(value.toByteArray(cp1251),Charsets.UTF_8,"windows-1251"))
        assertEquals("windows-1251",TextEncoding.language(0x419).name())
    }
    @Test fun chineseAndJapanese() {
        for((value,lcid) in listOf("日本語の説明" to 0x411,"技术手册" to 0x804,"技術手冊" to 0x404)) {
            val cs=TextEncoding.language(lcid); assertEquals(value,TextEncoding.decode(value.toByteArray(cs),cs))
        }
    }
    @Test fun relativeImagesCssAndAnchors() {
        assertEquals("/images/Схема 1.png",ArchivePath.resolve("../images/%D0%A1%D1%85%D0%B5%D0%BC%D0%B0%201.png","/pages/start.htm")!!.path)
        assertEquals("/pages/start.htm#pin",ArchivePath.resolve("#pin","/pages/start.htm")!!.reference)
        assertEquals("/css/main.css",ArchivePath.resolve("..\\css\\main.css","/pages/start.htm")!!.path)
        assertEquals("/images/a+b.png",ArchivePath.resolve("../images/a+b.png","/pages/start.htm")!!.path)
        assertEquals("/root.htm",ArchivePath.resolve("/root.htm","/pages/start.htm")!!.path)
    }
    @Test fun legacyInternalHelpUrls() {
        for(prefix in listOf("ms-its:","its:","mk:@MSITStore:")) assertEquals("/page.htm#p",ArchivePath.resolve("${prefix}C:\\docs\\Manual.CHM::/page.htm#p","/","manual.chm")!!.reference)
        assertNull(ArchivePath.resolve("ms-its:other.chm::/page.htm","/","manual.chm"))
    }
    @Test fun traversalAndUntrustedSchemes() {
        for(path in listOf("../../private.txt","/%2e%2e/secrets","/%252e%252e/secrets","/%2f%2fhost/file","file:///etc/passwd","content://x/y","javascript:alert(1)","https://example.org","//example.org","/a%00b","/a%5c..%5c..%5cx","/%zz")) assertNull(path,ArchivePath.resolve(path))
        assertEquals("/safe.htm",ArchivePath.resolve("../safe.htm","/dir/page.htm")!!.path)
    }
    @Test fun urlEncodingPreservesRootAndUnicode() {
        val path=TopicPath("/каталог/a b+1.htm","?q=1","#pin")
        val url=ArchivePath.url("book.chm.invalid",path)
        assertTrue(url.contains("%D0%BA")); assertTrue(url.contains("a%20b+1.htm?q=1#pin"))
    }
    private fun binary(cycle:Boolean=false):List<TocEntry> {
        val toc=ByteArray(4200); val t=ByteBuffer.wrap(toc).order(ByteOrder.LITTLE_ENDIAN)
        t.putInt(0,4096); t.putInt(4100,4); t.putInt(4104,0); t.putInt(4112,if(cycle) 4096 else 0); t.putInt(4116,4124)
        t.putInt(4128,8); t.putInt(4132,0)
        val titles="Раздел\u0000Страница\u0000".toByteArray(cp1251)
        val topics=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt("Раздел\u0000".toByteArray(cp1251).size).putInt(0).putInt(6).array()
        val urlTable=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(0).putInt(0).array()
        return BinaryTocParser.parse(toc,topics,titles,urlTable,ByteArray(8)+"page.htm\u0000".toByteArray(),cp1251,"manual.chm")
    }
    @Test fun binaryTocHasFoldersTopicsAndHierarchy() {
        val toc=binary(); assertEquals(2,toc.size); assertEquals("Раздел",toc[0].title); assertEquals("Страница",toc[1].title)
        assertNull(toc[0].target); assertEquals("/page.htm",toc[1].target); assertEquals(0,toc[1].parent)
    }
    @Test(expected=IllegalArgumentException::class) fun binaryCyclesAreRejected() { binary(true) }
    @Test(expected=IllegalArgumentException::class) fun truncatedBinaryIsRejected() { BinaryTocParser.parse(byteArrayOf(0,16,0,0),ByteArray(0),ByteArray(0),ByteArray(0),ByteArray(0),cp1251,"m.chm") }
    @Test fun systemMetadataUsesLanguageRecord() {
        val b=ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN); b.putInt(3)
        fun record(type:Int,bytes:ByteArray) { b.putShort(type.toShort()); b.putShort(bytes.size.toShort()); b.put(bytes) }
        record(3,"Справочник\u0000".toByteArray(cp1251)); record(0,"toc.hhc\u0000".toByteArray()); record(2,"index.htm\u0000".toByteArray())
        record(4,ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x419).array())
        val meta=SystemMetadata.parse(b.array().copyOf(b.position()))
        assertEquals("Справочник",meta.title); assertEquals("toc.hhc",meta.contents); assertEquals("index.htm",meta.home)
    }
}
