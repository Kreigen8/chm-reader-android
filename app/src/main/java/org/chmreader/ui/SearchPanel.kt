package org.chmreader.ui

import android.view.*
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.chmreader.ReaderModel
import org.chmreader.core.TocEntry
import org.chmreader.data.SearchHit

class SearchPanel(private val activity: AppCompatActivity, private val model: ReaderModel, private val open: (String,String) -> Unit) {
    private val dialog=BottomSheetDialog(activity)
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private var revision=0
    fun show() {
        val doc=model.document.value?.second ?: return
        val d=activity.resources.displayMetrics.density
        fun dp(n:Int)=(n*d).toInt()
        val root=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(12)) }
        val title=TextView(activity).apply { text="Поиск в справочнике"; textSize=22f; setPadding(0,0,0,dp(12)); androidx.core.view.ViewCompat.setAccessibilityHeading(this,true) }
        root.addView(title)
        val field=TextInputLayout(activity).apply { hint="Название раздела или текст"; boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val input=TextInputEditText(activity).apply { setSingleLine(); imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH; setText(model.searchQuery) }
        field.addView(input); root.addView(field,LinearLayout.LayoutParams(-1,-2))
        val tabs=RadioGroup(activity).apply { orientation=RadioGroup.HORIZONTAL }
        val titles=RadioButton(activity).apply { id=View.generateViewId(); text="Разделы"; minHeight=dp(48) }
        val full=RadioButton(activity).apply { id=View.generateViewId(); text="Весь текст"; minHeight=dp(48) }
        tabs.addView(titles); tabs.addView(full); tabs.check(titles.id); root.addView(tabs)
        val status=TextView(activity).apply { textSize=14f; setPadding(0,dp(8),0,dp(8)) }; root.addView(status)
        val indexing=MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        root.addView(indexing,LinearLayout.LayoutParams(-1,dp(48)))
        val list=RecyclerView(activity).apply { layoutManager=LinearLayoutManager(activity) }
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        val adapter=HitsAdapter { hit -> dialog.dismiss(); model.searchQuery=input.text.toString(); open(hit.path,model.searchQuery) }
        list.adapter=adapter
        fun updateIndex() {
            val p=model.indexProgress.value
            val running=model.indexing.value==true
            indexing.text=when { running -> "Отменить индексацию"; p?.complete==true && p.skipped==0 -> "Индекс готов"; else -> "Создать / продолжить индекс" }
            indexing.isEnabled=running || p?.complete!=true || p.skipped>0
            indexing.visibility=if(tabs.checkedRadioButtonId==full.id) View.VISIBLE else View.GONE
            if(tabs.checkedRadioButtonId==full.id) {
                status.text=when {
                    running -> "Индексация: ${p?.done ?: 0} из ${p?.total ?: 0}. Поиск уже доступен."
                    p?.complete==true -> "Проиндексировано ${p.done} страниц" + if(p.skipped>0) ". Не прочитано: ${p.skipped}." else "."
                    else -> "Индекс: ${p?.done ?: 0} из ${p?.total ?: 0} страниц. Для полного поиска создайте индекс."
                }
            }
        }
        fun search() {
            val q=input.text.toString().trim(); model.searchQuery=q; val ticket=++revision
            updateIndex()
            if(q.isEmpty()) { adapter.hits=emptyList(); if(tabs.checkedRadioButtonId==titles.id) status.text="Введите название раздела"; return }
            if(tabs.checkedRadioButtonId==titles.id) {
                val hits=doc.toc.withIndex().filter { it.value.title.contains(q,true) }.take(200).map { (i,e) -> SearchHit(e.title,e.target ?: "toc:$i","Уровень ${e.level+1}") }
                adapter.hits=hits; status.text=if(hits.isEmpty()) "Разделы не найдены" else "Найдено разделов: ${hits.size}"
            } else model.search(q) { hits -> if(dialog.isShowing && ticket==revision) { adapter.hits=hits; if(hits.isEmpty()) status.append("\nПо текущему индексу совпадений нет.") } }
        }
        val debounce=Runnable { search() }
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { handler.removeCallbacks(debounce); handler.postDelayed(debounce,220) }
            override fun afterTextChanged(s:Editable?)=Unit
        })
        tabs.setOnCheckedChangeListener { _,_ -> search() }
        input.setOnEditorActionListener { _,_,_ -> search(); true }
        indexing.setOnClickListener { if(model.indexing.value==true) model.cancelIndex() else model.startIndex(); updateIndex() }
        val observer=androidx.lifecycle.Observer<org.chmreader.data.IndexProgress?> { updateIndex(); if(tabs.checkedRadioButtonId==full.id) { handler.removeCallbacks(debounce); handler.postDelayed(debounce,400) } }
        val runningObserver=androidx.lifecycle.Observer<Boolean> { updateIndex() }
        model.indexProgress.observe(activity,observer); model.indexing.observe(activity,runningObserver)
        dialog.setContentView(root)
        dialog.setOnShowListener { dialog.behavior.peekHeight=(activity.resources.displayMetrics.heightPixels*0.9).toInt(); dialog.behavior.state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED; root.layoutParams.height=(activity.resources.displayMetrics.heightPixels*0.85).toInt() }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null); model.indexProgress.removeObserver(observer); model.indexing.removeObserver(runningObserver) }
        dialog.show(); search()
    }
    private class HitsAdapter(val select:(SearchHit)->Unit):RecyclerView.Adapter<HitsAdapter.Holder>() {
        var hits=emptyList<SearchHit>(); set(value) { field=value; notifyDataSetChanged() }
        class Holder(val root:LinearLayout,val title:TextView,val excerpt:TextView):RecyclerView.ViewHolder(root)
        override fun getItemCount()=hits.size
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder {
            val c=parent.context; val d=c.resources.displayMetrics.density
            val row=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL; setPadding(0,(14*d).toInt(),0,(14*d).toInt()); isFocusable=true; val v=android.util.TypedValue(); c.theme.resolveAttribute(android.R.attr.selectableItemBackground,v,true); setBackgroundResource(v.resourceId); layoutParams=RecyclerView.LayoutParams(-1,-2) }
            val title=TextView(c).apply { textSize=17f; setTypeface(null,android.graphics.Typeface.BOLD) }; val excerpt=TextView(c).apply { textSize=14f; maxLines=3 }
            row.addView(title); row.addView(excerpt); return Holder(row,title,excerpt)
        }
        override fun onBindViewHolder(h:Holder,p:Int) { val hit=hits[p]; h.title.text=hit.title; h.excerpt.text=hit.excerpt; h.root.setOnClickListener { select(hit) } }
    }
}
