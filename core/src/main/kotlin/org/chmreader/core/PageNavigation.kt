package org.chmreader.core

import kotlin.math.abs

enum class PageDirection(val step: Int) { PREVIOUS(-1), NEXT(1) }

/** Actual HTML pages in TOC preorder, independent of visited-history or collapsed branches. */
class ReadingOrder(references: List<String>, home: String? = null) {
    private fun page(reference: String) = ArchivePath.resolve(reference)?.path
    private val ordered = references.mapNotNull(::page).distinctBy(ArchivePath::key).let { pages ->
        val first = home?.let(::page)
        if (first != null && pages.none { it.equals(first, true) }) listOf(first) + pages else pages
    }
    val pages: List<String> get() = ordered
    private val indexes = ordered.withIndex().associate { ArchivePath.key(it.value) to it.index }
    fun adjacent(current: String?, direction: PageDirection): String? {
        val path = current?.let(::page) ?: return null
        val index = indexes[ArchivePath.key(path)] ?: return null
        return ordered.getOrNull(index + direction.step)
    }
}

/** Gesture policy in physical pixels; Android supplies density-adjusted thresholds. */
class PageSwipeGesture(private val touchSlop: Float, private val minDistance: Float, private val longPressMillis: Long, private val maxDurationMillis: Long = 1200) {
    private var active = false
    private var rejected = false
    private var moved = false
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var scrollPrevious = false
    private var scrollNext = false
    fun start(x: Float, y: Float, time: Long, canScrollPrevious: Boolean, canScrollNext: Boolean) {
        active = true; rejected = false; moved = false
        startX = x; startY = y; startTime = time
        scrollPrevious = canScrollPrevious; scrollNext = canScrollNext
    }
    fun cancel() { active = false; rejected = true }
    fun move(x: Float, y: Float, time: Long) {
        if (!active || rejected) return
        val dx = x - startX; val dy = y - startY
        if (!moved && time - startTime >= longPressMillis) { rejected = true; return }
        if (!moved && maxOf(abs(dx), abs(dy)) > touchSlop) {
            moved = true
            // Once a scroll starts, do not reinterpret its end as a page turn.
            if (abs(dy) >= abs(dx) || (dx < 0 && scrollNext) || (dx > 0 && scrollPrevious)) rejected = true
        }
        if (moved && ((dx < 0 && scrollNext) || (dx > 0 && scrollPrevious) || abs(dy) > minDistance || abs(dy) > abs(dx))) rejected = true
    }
    fun finish(x: Float, y: Float, time: Long): PageDirection? {
        move(x, y, time)
        val dx = x - startX; val dy = y - startY
        val valid = active && !rejected && moved && time - startTime in 1..maxDurationMillis && abs(dx) >= minDistance && abs(dx) >= abs(dy) * 1.8f
        active = false
        return if (!valid) null else if (dx < 0) PageDirection.NEXT else PageDirection.PREVIOUS
    }
}
