package org.chmreader.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import org.chmreader.core.PageDirection
import org.chmreader.core.PageSwipeGesture

class ReaderWebView(context: Context) : WebView(context) {
    var onPageSwipe: ((PageDirection) -> Boolean)? = null
    private var gesture: PageSwipeGesture? = null
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val density = resources.displayMetrics.density
                gesture = PageSwipeGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat(), maxOf(80 * density, width * 0.22f), ViewConfiguration.getLongPressTimeout().toLong()).also {
                    it.start(event.x, event.y, event.eventTime, canScrollHorizontally(-1), canScrollHorizontally(1))
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> gesture?.cancel()
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) gesture?.move(event.x, event.y, event.eventTime) else gesture?.cancel()
            MotionEvent.ACTION_CANCEL -> { gesture?.cancel(); gesture = null }
            MotionEvent.ACTION_UP -> {
                val direction = gesture?.finish(event.x, event.y, event.eventTime)
                gesture = null
                if (direction != null && onPageSwipe?.invoke(direction) == true) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    try { super.onTouchEvent(cancel) } finally { cancel.recycle() }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
