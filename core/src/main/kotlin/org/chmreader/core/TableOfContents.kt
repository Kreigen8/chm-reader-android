package org.chmreader.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.nio.charset.Charset

/** Preorder tree: parent indexes refer to previous entries, so there is no recursion limit. */
data class TocEntry(val id: String, val title: String, val target: String?, val parent: Int?, val level: Int)

object HhcParser {
    fun parse(html: String, source: String, archiveName: String): List<TocEntry> {
        val result = ArrayList<TocEntry>()
        data class Group(val parent: Int?, var last: Int? = null)
        val groups = ArrayDeque<Group>(); groups.add(Group(null))
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node !is Element) return
                when (node.normalName()) {
                    "ul", "ol" -> groups.addLast(Group(groups.last().last ?: groups.last().parent))
                    "object" -> if (node.attr("type").equals("text/sitemap", true)) {
                        val params = node.select("param").associate { it.attr("name").lowercase() to it.attr("value") }
                        val title = params["name"]?.trim().orEmpty()
                        val local = params["local"]?.takeIf { it.isNotBlank() }
                        val merge = params["merge"]
                        if (title.isNotBlank() || local != null || merge != null) {
                            val parent = groups.last().parent
                            val target = local?.let { ArchivePath.resolve(it, source, archiveName)?.reference }
                            val label = title.ifBlank { local ?: "Вложенный справочник: $merge" }
                            result.add(TocEntry("h${result.size}", label, target, parent, parent?.let { result[it].level + 1 } ?: 0))
                            groups.last().last = result.lastIndex
                        }
                    }
                }
            }
            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.normalName() in setOf("ul", "ol") && groups.size > 1) groups.removeLast()
            }
        }, Jsoup.parse(html))
        return result
    }
}

/** Bounds-checked implementation of the documented #TOCIDX/#TOPICS metadata, not LZX. */
object BinaryTocParser {
    fun parse(toc: ByteArray, topics: ByteArray, strings: ByteArray, urlTable: ByteArray, urls: ByteArray, charset: Charset, archiveName: String): List<TocEntry> {
        fun u32(b: ByteArray, p: Int): Int {
            require(p >= 0 && p.toLong() + 4 <= b.size) { "Повреждено бинарное оглавление" }
            val v = (0..3).fold(0L) { n, i -> n or ((b[p+i].toLong() and 255) shl (8*i)) }
            require(v <= Int.MAX_VALUE) { "Недопустимое смещение в оглавлении" }
            return v.toInt()
        }
        fun text(b: ByteArray, p: Int): String {
            require(p in b.indices) { "Строка за пределами оглавления" }
            var end = p
            while (end < b.size && b[end] != 0.toByte()) end++
            require(end < b.size) { "Незавершённая строка оглавления" }
            return TextEncoding.decode(b.copyOfRange(p, end), charset)
        }
        data class Link(val offset: Int, val parent: Int?)
        val stack = ArrayDeque<Link>(); stack.add(Link(u32(toc, 0), null))
        val visited = HashSet<Int>(); val result = ArrayList<TocEntry>()
        while (stack.isNotEmpty()) {
            val (offset, parent) = stack.removeLast()
            if (offset == 0) continue
            require(visited.add(offset)) { "Цикл в бинарном оглавлении" }
            require(offset >= 4096 && offset.toLong()+20 <= toc.size)
            val flags = u32(toc, offset+4); val index = u32(toc, offset+8)
            var title = ""; var target: String? = null
            if (flags and 8 != 0) {
                val t = Math.multiplyExact(index, 16)
                val nameOffset = u32(topics, t+4)
                title = text(strings, nameOffset)
                val urlOffset = u32(urlTable, u32(topics, t+8)+8)
                target = ArchivePath.resolve(text(urls, urlOffset+8), "/", archiveName)?.reference
            } else if (flags and 4 != 0) title = text(strings, index)
            val current = if (title.isNotBlank() || target != null) {
                result.add(TocEntry("b$offset", title.ifBlank { target.orEmpty() }, target, parent, parent?.let { result[it].level+1 } ?: 0))
                result.lastIndex
            } else parent
            stack.add(Link(u32(toc, offset+16), parent))
            if (flags and 4 != 0) stack.add(Link(u32(toc, offset+20), current))
        }
        return result
    }
}

object TocTree {
    fun children(entries: List<TocEntry>): Set<Int> = entries.mapNotNull { it.parent }.toSet()
    fun visible(entries: List<TocEntry>, expanded: Set<String>): List<Int> {
        val shown = BooleanArray(entries.size)
        return entries.indices.filter { i ->
            val p = entries[i].parent
            (p == null || (shown[p] && entries[p].id in expanded)).also { shown[i] = it }
        }
    }
    fun ancestors(entries: List<TocEntry>, index: Int): List<String> {
        val result = ArrayList<String>(); var parent = entries.getOrNull(index)?.parent
        while (parent != null) { result.add(entries[parent].id); parent = entries[parent].parent }
        return result
    }
}
