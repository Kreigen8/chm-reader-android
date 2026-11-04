package org.chmreader.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import android.content.Context
import androidx.core.graphics.PathParser
import com.google.android.material.color.MaterialColors

class ReaderIcon(context: Context, name: String) : Drawable() {
    private val iconSize=(24*context.resources.displayMetrics.density+0.5f).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.DKGRAY) }
    private val path = PathParser.createPathFromPathData(when(name) {
        "open" -> "M10,4H2v16h20V6H12zM4,8h16v10H4z"
        "search" -> "M9.5,3a6.5,6.5 0,1 0,4.1 11.55L20,21l1,-1 -6.45,-6.4A6.5,6.5 0,0 0,9.5 3M9.5,5a4.5,4.5 0,1 1,0 9a4.5,4.5 0,1 1,0 -9"
        "back" -> "M20,11H7.8l5.6,-5.6L12,4l-8,8 8,8 1.4,-1.4L7.8,13H20z"
        "forward" -> "M4,11h12.2l-5.6,-5.6L12,4l8,8 -8,8 -1.4,-1.4L16.2,13H4z"
        "menu" -> "M3,5h18v2H3zM3,11h18v2H3zM3,17h18v2H3z"
        "more" -> "M10,3h4v4h-4zM10,10h4v4h-4zM10,17h4v4h-4z"
        "expand" -> "M8,4l8,8 -8,8 -1.4,-1.4L13.2,12 6.6,5.4z"
        "collapse" -> "M4,8l8,8 8,-8 -1.4,-1.4L12,13.2 5.4,6.6z"
        "close" -> "M5.4,4L12,10.6 18.6,4 20,5.4 13.4,12 20,18.6 18.6,20 12,13.4 5.4,20 4,18.6 10.6,12 4,5.4z"
        "home" -> "M3,10L12,2l9,8v12h-7v-8h-4v8H3zM5,11v9h3v-8h8v8h3v-9l-7,-6z"
        "locate" -> "M11,2h2v3a7,7 0,0 1,6 6h3v2h-3a7,7 0,0 1,-6 6v3h-2v-3a7,7 0,0 1,-6 -6H2v-2h3a7,7 0,0 1,6 -6zM12,7a5,5 0,1 0,0 10a5,5 0,1 0,0 -10M10,10h4v4h-4z"
        else -> "M4,3h16v18H4zM6,5v14h12V5zM8,8h8v2H8zM8,12h8v2H8z"
    })!!
    override fun draw(canvas: Canvas) { canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f); canvas.drawPath(path,paint); canvas.restore() }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter=filter }
    @Deprecated("Deprecated") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = iconSize
    override fun getIntrinsicHeight() = iconSize
}
