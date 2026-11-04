package org.chmreader.ui

import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.chmreader.core.*

class TocAdapter(private val entries: List<TocEntry>, val expanded: MutableSet<String>, private val change: () -> Unit, private val open: (TocEntry) -> Unit) : RecyclerView.Adapter<TocAdapter.Holder>() {
    private val parents = TocTree.children(entries)
    private var visible = TocTree.visible(entries, expanded)
    var current: String? = null
    class Holder(val row: LinearLayout, val expand: ImageButton, val label: TextView) : RecyclerView.ViewHolder(row)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
        val c=parent.context; val d=c.resources.displayMetrics.density
        val row=LinearLayout(c).apply { gravity=Gravity.CENTER_VERTICAL; minimumHeight=(52*d).toInt(); layoutParams=RecyclerView.LayoutParams(-1,-2) }
        val button=ImageButton(c).apply { setBackgroundResource(android.R.attr.selectableItemBackgroundBorderless.let { attr -> val v=android.util.TypedValue(); c.theme.resolveAttribute(attr,v,true); v.resourceId }); scaleType=ImageView.ScaleType.CENTER_INSIDE; setPadding((12*d).toInt(),(12*d).toInt(),(12*d).toInt(),(12*d).toInt()) }
        row.addView(button,LinearLayout.LayoutParams((48*d).toInt(),(48*d).toInt()))
        val label=TextView(c).apply { textSize=16f; gravity=Gravity.CENTER_VERTICAL; minimumHeight=(52*d).toInt(); setPadding(0,(8*d).toInt(),(12*d).toInt(),(8*d).toInt()); setTextColor(MaterialColors.getColor(c,com.google.android.material.R.attr.colorOnSurface,0)); isFocusable=true; val v=android.util.TypedValue(); c.theme.resolveAttribute(android.R.attr.selectableItemBackground,v,true); setBackgroundResource(v.resourceId) }
        row.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        return Holder(row,button,label)
    }
    override fun getItemCount()=visible.size
    override fun onBindViewHolder(h: Holder, position: Int) {
        val index=visible[position]; val e=entries[index]; val c=h.row.context; val d=c.resources.displayMetrics.density
        h.row.setPadding((minOf(e.level,6)*14*d).toInt(),0,0,0)
        val isGroup=index in parents; val selected=e.target?.substringBefore('#')?.substringBefore('?')?.equals(current?.substringBefore('#')?.substringBefore('?'),true)==true
        h.row.setBackgroundColor(if(selected) MaterialColors.getColor(c,com.google.android.material.R.attr.colorPrimaryContainer,0) else android.graphics.Color.TRANSPARENT)
        h.label.text=e.title; h.label.isSelected=selected
        h.label.contentDescription="${e.title}, уровень ${e.level+1}" + if(selected) ", текущий раздел" else ""
        h.label.setTypeface(null,if(selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        h.expand.visibility=if(isGroup) View.VISIBLE else View.INVISIBLE
        h.expand.isFocusable=isGroup
        h.expand.setImageDrawable(ReaderIcon(c,if(e.id in expanded) "collapse" else "expand"))
        h.expand.contentDescription=(if(e.id in expanded) "Свернуть: " else "Развернуть: ")+e.title
        h.expand.setOnClickListener { toggle(e) }
        h.label.setOnClickListener { if(e.target!=null) open(e) else if(isGroup) toggle(e) }
    }
    fun toggle(e: TocEntry) { if(!expanded.add(e.id)) expanded.remove(e.id); refresh(); change() }
    fun refresh() { visible=TocTree.visible(entries,expanded); notifyDataSetChanged() }
    fun reveal(path: String?): Int {
        current=path
        val index=entries.indexOfFirst { it.target?.substringBefore('#')?.substringBefore('?')?.equals(path?.substringBefore('#')?.substringBefore('?'),true)==true }
        if(index>=0) expanded.addAll(TocTree.ancestors(entries,index))
        refresh(); change(); return visible.indexOf(index)
    }
    fun revealIndex(index:Int):Int {
        val entry=entries.getOrNull(index) ?: return -1
        expanded.addAll(TocTree.ancestors(entries,index)); expanded.add(entry.id)
        refresh(); change(); return visible.indexOf(index)
    }
}
