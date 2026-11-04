package org.chmreader.core

import org.junit.Assert.*
import org.junit.Test

class PageNavigationTest {
    @Test fun readingFollowsTocRatherThanHistoryOrFilename() {
        val order=ReadingOrder(listOf("/z.htm#first","/nested/b.htm","/a.htm","/z.htm#second","/A.HTM?view=1"))
        assertEquals(listOf("/z.htm","/nested/b.htm","/a.htm"),order.pages)
        assertEquals("/nested/b.htm",order.adjacent("/z.htm#anything",PageDirection.NEXT))
        assertEquals("/z.htm",order.adjacent("/nested/b.htm",PageDirection.PREVIOUS))
        assertNull(order.adjacent("/z.htm",PageDirection.PREVIOUS))
        assertNull(order.adjacent("/A.HTM?view=1",PageDirection.NEXT))
        assertNull(order.adjacent("/unlisted.htm",PageDirection.NEXT))
    }
    @Test fun homeBeforeContentsAndCyrillicPaths() {
        val order=ReadingOrder(listOf("/Начало.htm","/Раздел.htm"),"/index.htm")
        assertEquals("/Начало.htm",order.adjacent("/index.htm",PageDirection.NEXT))
        assertEquals("/Раздел.htm",order.adjacent("/%D0%9D%D0%B0%D1%87%D0%B0%D0%BB%D0%BE.htm",PageDirection.NEXT))
        assertEquals(listOf("/a.htm","/home.htm"),ReadingOrder(listOf("/a.htm","/home.htm"),"/home.htm").pages)
    }
    private fun gesture(previous:Boolean=false,next:Boolean=false)=PageSwipeGesture(8f,80f,500).apply { start(250f,200f,0,previous,next) }
    @Test fun leftGoesNextAndRightGoesPrevious() {
        val left=gesture(); left.move(200f,202f,50)
        assertEquals(PageDirection.NEXT,left.finish(120f,205f,200))
        val right=gesture(); right.move(300f,198f,50)
        assertEquals(PageDirection.PREVIOUS,right.finish(390f,194f,200))
        assertNull(right.finish(390f,194f,201))
    }
    @Test fun wideTablesPanUntilANewSwipeAtTheEdge() {
        val pan=gesture(next=true); pan.move(190f,200f,50)
        assertNull(pan.finish(80f,200f,200))
        val edge=gesture(previous=true,next=false); edge.move(190f,200f,50)
        assertEquals(PageDirection.NEXT,edge.finish(80f,200f,200))
        val reversed=gesture(previous=true); reversed.move(220f,200f,40); reversed.move(310f,200f,100)
        assertNull(reversed.finish(400f,200f,200))
    }
    @Test fun pinchVerticalSelectionAndCancelledTouchesNeverTurnPages() {
        val pinch=gesture(); pinch.move(200f,200f,50); pinch.cancel(); assertNull(pinch.finish(80f,200f,200))
        val vertical=gesture(); vertical.move(249f,230f,40); assertNull(vertical.finish(80f,300f,200))
        val selection=gesture(); selection.move(250f,200f,600); assertNull(selection.finish(80f,200f,800))
        assertNull(gesture().finish(249f,201f,100))
        val diagonal=gesture(); diagonal.move(200f,220f,50); assertNull(diagonal.finish(100f,300f,200))
        val slow=gesture(); slow.move(200f,200f,40); assertNull(slow.finish(80f,200f,1300))
    }
    @Test fun smallJitterBeforeHorizontalMotionIsNotVerticalScroll() {
        val g=gesture(); g.move(250f,202f,10); g.move(200f,203f,30)
        assertEquals(PageDirection.NEXT,g.finish(90f,205f,150))
    }
}
