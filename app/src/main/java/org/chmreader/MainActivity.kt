package org.chmreader

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import org.chmreader.chm.ChmDocument
import org.chmreader.core.*
import org.chmreader.data.*
import org.chmreader.ui.*

class MainActivity : AppCompatActivity() {
    private lateinit var model: ReaderModel
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawer: DrawerLayout
    private lateinit var split: LinearLayout
    private lateinit var tree: LinearLayout
    private lateinit var treeList: RecyclerView
    private lateinit var treeSubtitle: TextView
    private lateinit var pageFrame: FrameLayout
    private lateinit var web: ReaderWebView
    private lateinit var welcome: LinearLayout
    private lateinit var divider: View
    private lateinit var progress: LinearProgressIndicator
    private lateinit var busyLabel: TextView
    private lateinit var cancelImport: MaterialButton
    private lateinit var backward: ImageButton
    private lateinit var forward: ImageButton
    private lateinit var pageTitle: TextView
    private lateinit var highlightBar: LinearLayout
    private lateinit var highlightLabel: TextView
    private var adapter: TocAdapter? = null
    private var book: Book? = null
    private var doc: ChmDocument? = null
    private var client: DocumentWebClient? = null
    private var current: String? = null
    private var highlight = ""
    private var highlightTerms = emptyList<String>()
    private var expanded = mutableSetOf<String>()
    private var wide = false
    private var restoreScroll: Pair<String,Int>? = null
    private var savedWeb: Bundle? = null
    private var savedBookId: String? = null
    private var resetHistory = false
    private var readingOrder = ReadingOrder(emptyList())
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            model.import(uri)
        }
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun color(attr:Int)=MaterialColors.getColor(this,attr,Color.DKGRAY)
    override fun onCreate(state: Bundle?) {
        val prefs=getSharedPreferences("reader",MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(prefs.getInt("theme",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        model=ViewModelProvider(this)[ReaderModel::class.java]
        savedWeb=state?.getBundle("web"); savedBookId=state?.getString("book")
        highlight=state?.getString("highlight").orEmpty()
        buildUi()
        model.document.observe(this) { pair -> pair?.let { display(it.first,it.second) } }
        model.busy.observe(this) { message ->
            progress.visibility=if(message==null) View.GONE else View.VISIBLE
            busyLabel.text=message; busyLabel.visibility=if(message==null) View.GONE else View.VISIBLE
            cancelImport.visibility=if(message?.startsWith("Коп") == true || message?.startsWith("Скоп") == true) View.VISIBLE else View.GONE
        }
        model.error.observe(this) { message -> if(message!=null) { MaterialAlertDialogBuilder(this).setTitle("Не удалось выполнить действие").setMessage(message).setPositiveButton("Понятно",null).show(); model.error.value=null } }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                    highlightBar.visibility==View.VISIBLE -> clearHighlight()
                    !resetHistory && web.canGoBack() -> web.goBack()
                    else -> { isEnabled=false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
        if(state==null && intent?.action==Intent.ACTION_VIEW && intent.data!=null) model.import(intent.data!!)
        else model.restoreLast()
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); if(intent.action==Intent.ACTION_VIEW) intent.data?.let(model::import) }
    private fun icon(name:String,description:String,action:()->Unit):ImageButton = ImageButton(this).apply {
        setImageDrawable(ReaderIcon(this@MainActivity,name)); contentDescription=description
        val value=android.util.TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,value,true); setBackgroundResource(value.resourceId)
        scaleType=ImageView.ScaleType.CENTER_INSIDE; setPadding(dp(12),dp(12),dp(12),dp(12)); minimumWidth=dp(48); minimumHeight=dp(48)
        setOnClickListener { action() }
    }
    private fun buildUi() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(color(com.google.android.material.R.attr.colorSurface)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val system=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(system.left,system.top,system.right,maxOf(system.bottom,ime.bottom)); insets
        }
        toolbar=MaterialToolbar(this).apply {
            title="CHM Reader"; navigationIcon=ReaderIcon(this@MainActivity,"menu"); navigationContentDescription="Оглавление"
            setNavigationOnClickListener { if(doc!=null) { if(wide) revealCurrent() else drawer.openDrawer(GravityCompat.START) } else recent() }
            menu.add(0,1,0,"Поиск").apply { icon=ReaderIcon(this@MainActivity,"search"); setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
            menu.add(0,2,1,"Открыть CHM").apply { icon=ReaderIcon(this@MainActivity,"open"); setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
            menu.add(0,3,2,"Недавние справочники")
            menu.add(0,4,3,"Тема приложения")
            menu.add(0,5,4,"Кодировка страницы")
            menu.add(0,6,5,"Размер текста")
            menu.add(0,7,6,"Информация о справочнике")
            menu.add(0,8,7,"О приложении и лицензии")
            menu.add(0,9,8,"Обновления")
            setOnMenuItemClickListener { item -> when(item.itemId) {
                1 -> if(doc!=null) SearchPanel(this@MainActivity,model) { path,query ->
                    if(path.startsWith("toc:")) {
                        val position=adapter?.revealIndex(path.substringAfter(':').toIntOrNull() ?: -1) ?: -1
                        if(position>=0) treeList.scrollToPosition(position)
                        if(!wide) drawer.openDrawer(GravityCompat.START)
                    } else { open(path); showHighlight(query) }
                }.show() else message("Сначала откройте справочник")
                2 -> chooseFile()
                3 -> recent()
                4 -> chooseTheme()
                5 -> chooseEncoding()
                6 -> chooseTextSize()
                7 -> documentInfo()
                8 -> about()
                9 -> startActivity(Intent(this@MainActivity,UpdateActivity::class.java))
            }; true }
        }
        root.addView(toolbar,LinearLayout.LayoutParams(-1,dp(64)))
        progress=LinearProgressIndicator(this).apply { isIndeterminate=true; visibility=View.GONE }
        root.addView(progress,LinearLayout.LayoutParams(-1,dp(4)))
        val busyRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        busyLabel=TextView(this).apply { textSize=14f; visibility=View.GONE; setPadding(dp(16),dp(8),dp(8),dp(8)) }
        busyRow.addView(busyLabel,LinearLayout.LayoutParams(0,-2,1f))
        cancelImport=MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text="Отменить"; visibility=View.GONE; setOnClickListener { model.cancelImport() } }
        busyRow.addView(cancelImport); root.addView(busyRow)
        drawer=DrawerLayout(this)
        split=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        pageFrame=FrameLayout(this)
        web=ReaderWebView(this).apply {
            onPageSwipe=::turnPage
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled=false; domStorageEnabled=false; databaseEnabled=false; blockNetworkLoads=true
                allowFileAccess=false; allowContentAccess=false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs=false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs=false
                mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically=false
                setSupportZoom(true); builtInZoomControls=true; displayZoomControls=false
                useWideViewPort=true; loadWithOverviewMode=true; cacheMode=WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture=true; textZoom=model.library.preferences.getInt("textZoom",100)
            }
            CookieManager.getInstance().setAcceptCookie(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
            if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings,false)
            @Suppress("DEPRECATION")
            if(android.os.Build.VERSION.SDK_INT in 29..32) settings.forceDark=WebSettings.FORCE_DARK_OFF
            setDownloadListener { _,_,_,_,_ -> message("Загрузка вложений отключена. HTML-страницы и изображения открываются внутри справочника.") }
            setOnScrollChangeListener { _,_,scrollY,_,_ -> savePosition(scrollY) }
        }
        pageFrame.addView(web,FrameLayout.LayoutParams(-1,-1))
        welcome=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(28),dp(24),dp(28),dp(24)); setBackgroundColor(color(com.google.android.material.R.attr.colorSurface)) }
        pageFrame.addView(welcome,FrameLayout.LayoutParams(-1,-1)); showWelcome()
        split.addView(pageFrame,LinearLayout.LayoutParams(0,-1,1f)); drawer.addView(split,DrawerLayout.LayoutParams(-1,-1))
        tree=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(color(com.google.android.material.R.attr.colorSurface)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(12),dp(4),dp(4)) }
        val heading=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        heading.addView(TextView(this).apply { text="Содержание"; textSize=22f; ViewCompat.setAccessibilityHeading(this,true) })
        treeSubtitle=TextView(this).apply { textSize=13f; setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant)) }; heading.addView(treeSubtitle)
        header.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(icon("locate","Показать текущий раздел") { revealCurrent() },LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(icon("more","Действия с оглавлением") { anchorMenu(it = header) },LinearLayout.LayoutParams(dp(48),dp(48)))
        tree.addView(header)
        treeList=RecyclerView(this).apply { layoutManager=LinearLayoutManager(this@MainActivity); setHasFixedSize(false) }
        tree.addView(treeList,LinearLayout.LayoutParams(-1,0,1f))
        drawer.addView(tree,DrawerLayout.LayoutParams(dp(340),-1,GravityCompat.START))
        divider=FrameLayout(this).apply {
            addView(View(this@MainActivity).apply { setBackgroundColor(color(com.google.android.material.R.attr.colorOutline)) },FrameLayout.LayoutParams(dp(1),-1,Gravity.CENTER))
            contentDescription="Ширина оглавления. Перетащите или нажмите для изменения"; isFocusable=true; isClickable=true
            var downX=0f; var initialWidth=0; var moved=false
            setOnTouchListener { view,event -> when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=event.rawX; initialWidth=tree.width; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val delta=event.rawX-downX
                    if(kotlin.math.abs(delta)>dp(4)) moved=true
                    if(moved) setPanelWidth(initialWidth+delta.toInt())
                    true
                }
                MotionEvent.ACTION_UP -> { if(!moved) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            } }
            setOnClickListener { setPanelWidth(dp(if(tree.width>dp(330)) 280 else 380)) }
        }
        root.addView(drawer,LinearLayout.LayoutParams(-1,0,1f))
        highlightBar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; visibility=View.GONE }
        highlightLabel=TextView(this).apply {
            textSize=14f; maxLines=2; minimumHeight=dp(48); gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),0,0,0)
            setOnClickListener { if(highlightTerms.size>1) MaterialAlertDialogBuilder(this@MainActivity).setTitle("Слово для подсветки").setItems(highlightTerms.toTypedArray()) { _,index -> highlight=highlightTerms[index]; highlightLabel.text="Поиск: $highlight"; web.findAllAsync(highlight) }.setNegativeButton("Закрыть",null).show() }
        }
        highlightBar.addView(highlightLabel,LinearLayout.LayoutParams(0,-2,1f))
        highlightBar.addView(icon("back","Предыдущее совпадение") { web.findNext(false) },LinearLayout.LayoutParams(dp(48),dp(48)))
        highlightBar.addView(icon("forward","Следующее совпадение") { web.findNext(true) },LinearLayout.LayoutParams(dp(48),dp(48)))
        highlightBar.addView(icon("close","Закрыть подсветку") { clearHighlight() },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(highlightBar)
        val bottom=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),0,dp(4),0) }
        backward=icon("back","Назад по истории") { if(!resetHistory && web.canGoBack()) web.goBack() }
        forward=icon("forward","Вперёд по истории") { if(!resetHistory && web.canGoForward()) web.goForward() }
        bottom.addView(backward,LinearLayout.LayoutParams(dp(48),dp(48))); bottom.addView(forward,LinearLayout.LayoutParams(dp(48),dp(48)))
        pageTitle=TextView(this).apply { text="Выберите справочник"; textSize=13f; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; setPadding(dp(12),0,dp(8),0) }
        bottom.addView(pageTitle,LinearLayout.LayoutParams(0,-2,1f))
        bottom.addView(icon("home","Начальная страница") { doc?.home?.let(::open) },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(bottom,LinearLayout.LayoutParams(-1,-2))
        setContentView(root); ViewCompat.requestApplyInsets(root)
        root.addOnLayoutChangeListener { _,left,_,right,_,_,_,_,_ -> adapt(right-left) }
        updateNavigation()
    }
    private fun adapt(width:Int) {
        val available=width-root.paddingLeft-root.paddingRight
        val expanded=available>=dp(840)
        if(expanded==wide && tree.parent!=null) {
            if(wide) {
                val clamped=tree.layoutParams.width.coerceIn(dp(240),maxOf(dp(240),available-dp(368)))
                if(clamped!=tree.layoutParams.width) tree.layoutParams=LinearLayout.LayoutParams(clamped,-1)
            }
            return
        }
        wide=expanded
        (tree.parent as? android.view.ViewGroup)?.removeView(tree)
        (divider.parent as? android.view.ViewGroup)?.removeView(divider)
        if(wide) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            val panel=model.library.preferences.getInt("panelWidth",320).coerceIn(240,(available/resources.displayMetrics.density).toInt()-368)
            split.addView(tree,0,LinearLayout.LayoutParams(dp(panel),-1))
            split.addView(divider,1,LinearLayout.LayoutParams(dp(48),-1))
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            drawer.addView(tree,DrawerLayout.LayoutParams(minOf(dp(360),(width*0.9).toInt()),-1,GravityCompat.START))
        }
    }
    private fun setPanelWidth(value:Int) {
        val available=root.width-root.paddingLeft-root.paddingRight
        val width=value.coerceIn(dp(240),maxOf(dp(240),available-dp(368)))
        tree.layoutParams=LinearLayout.LayoutParams(width,-1)
        model.library.preferences.edit().putInt("panelWidth",(width/resources.displayMetrics.density).toInt()).apply()
    }
    private fun showWelcome() {
        welcome.removeAllViews()
        welcome.addView(TextView(this).apply { text="Ваши справочники"; textSize=30f; setTypeface(null,android.graphics.Typeface.BOLD); ViewCompat.setAccessibilityHeading(this,true) })
        welcome.addView(TextView(this).apply { text="Техническая документация,\nвсегда под рукой."; textSize=18f; setPadding(0,dp(12),0,dp(24)) })
        welcome.addView(MaterialButton(this).apply { text="Открыть CHM"; icon=ReaderIcon(this@MainActivity,"open"); setOnClickListener { chooseFile() } },LinearLayout.LayoutParams(-1,-2))
        welcome.addView(TextView(this).apply { text="Файлы хранятся на устройстве.\nБез интернета, рекламы и подписок."; textSize=14f; setPadding(0,dp(20),0,dp(24)); setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant)) })
        model.library.books().take(3).forEach { b -> welcome.addView(MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text=b.name; maxLines=2; gravity=Gravity.START or Gravity.CENTER_VERTICAL; setOnClickListener { model.open(b) } },LinearLayout.LayoutParams(-1,-2)) }
    }
    private fun display(b:Book, document:ChmDocument) {
        if(doc===document) return
        book=b; doc=document; welcome.visibility=View.GONE
        fun isReadable(reference:String):Boolean {
            val path=ArchivePath.resolve(reference,"/",document.name)?.path ?: return false
            return document.archive.entry(path)!=null && Regex("\\.(?:html?|xhtml)$",RegexOption.IGNORE_CASE).containsMatchIn(path)
        }
        readingOrder=ReadingOrder(document.toc.mapNotNull { it.target }.filter(::isReadable),document.home?.takeIf(::isReadable))
        toolbar.title=document.metadata.title.ifBlank { b.name.removeSuffix(".chm") }; toolbar.subtitle=b.name
        val state=model.library.state(b)
        expanded=state.getStringSet("expanded",emptySet())!!.toMutableSet()
        adapter=TocAdapter(document.toc,expanded,{ state.edit().putStringSet("expanded",expanded.toSet()).apply() }) { entry -> entry.target?.let(::open); if(!wide) drawer.closeDrawer(GravityCompat.START) }
        treeList.adapter=adapter
        treeSubtitle.text="${document.toc.size} разделов"
        val host="book-${b.id.take(24)}.chm.invalid"
        client=DocumentWebClient(document,host,{ state.getString("encoding",null) },::open,::confirmExternal,{ path ->
            current=path
            val selected=document.toc.firstOrNull { it.target?.substringBefore('#')?.equals(path.substringBefore('#'),true)==true }
            pageTitle.text=selected?.title ?: Uri.parse(path).lastPathSegment
            adapter?.current=path; adapter?.refresh(); state.edit().putString("page",path).apply(); updateNavigation()
        }, {
            if(resetHistory && web.url?.let { client?.internal(Uri.parse(it)) }==true) { web.clearHistory(); resetHistory=false }
            updateNavigation()
            if(highlight.isNotEmpty()) web.findAllAsync(highlight)
            restoreScroll?.let { (target,y) ->
                if(current?.substringBefore('#')==target.substringBefore('#')) {
                    web.postVisualStateCallback(0,object:WebView.VisualStateCallback() { override fun onComplete(requestId:Long) { web.scrollTo(0,y) } })
                }
                restoreScroll=null
            }
        })
        web.stopLoading(); web.webViewClient=client!!; resetHistory=true; updateNavigation()
        val target=state.getString("page",null)?.takeIf { ArchivePath.resolve(it,"/",document.name)?.let { p -> document.archive.entry(p.path)!=null }==true } ?: document.home
        if(savedBookId==b.id && savedWeb!=null && web.restoreState(savedWeb!!)!=null) { resetHistory=false; savedWeb=null; savedBookId=null }
        else if(target!=null) {
            restoreScroll=target to state.getInt("scroll",0); open(target)
        } else message("В этом справочнике не найдены HTML-страницы")
        if(highlight.isNotBlank()) showHighlight(highlight)
        document.warning?.let { message(it) }
    }
    fun open(reference:String) {
        val document=doc ?: return
        val target=ArchivePath.resolve(reference,current ?: "/",document.name)
        if(target==null) { message("Ссылка ведёт за пределы открытого CHM или в другой справочник"); return }
        web.loadUrl(ArchivePath.url(client!!.host,target))
    }
    private fun turnPage(direction:PageDirection):Boolean {
        if(doc==null || model.busy.value!=null || resetHistory || web.progress<100) return false
        val next=readingOrder.adjacent(current,direction) ?: return false
        restoreScroll=null
        open(next)
        val row=adapter?.reveal(next) ?: -1
        if(wide && row>=0) treeList.scrollToPosition(row)
        return true
    }
    private fun updateNavigation() {
        if(!::web.isInitialized) return
        backward.isEnabled=!resetHistory && web.canGoBack(); backward.alpha=if(backward.isEnabled) 1f else .35f
        forward.isEnabled=!resetHistory && web.canGoForward(); forward.alpha=if(forward.isEnabled) 1f else .35f
    }
    private fun savePosition(y:Int=web.scrollY) { book?.let { model.library.state(it).edit().putInt("scroll",y).apply() } }
    private fun revealCurrent() { val p=adapter?.reveal(current) ?: -1; if(p>=0) treeList.scrollToPosition(p); if(!wide && !drawer.isDrawerOpen(GravityCompat.START)) drawer.openDrawer(GravityCompat.START) }
    private fun anchorMenu(it:View) {
        PopupMenu(this,it).apply {
            menu.add("Свернуть всё").setOnMenuItemClickListener { expanded.clear(); adapter?.refresh(); book?.let { b -> model.library.state(b).edit().putStringSet("expanded",emptySet()).apply() }; true }
            menu.add("Развернуть всё").setOnMenuItemClickListener { doc?.toc?.let { entries -> expanded.addAll(TocTree.children(entries).map { i -> entries[i].id }) }; adapter?.refresh(); book?.let { b -> model.library.state(b).edit().putStringSet("expanded",expanded.toSet()).apply() }; true }
            menu.add("Текущий раздел").setOnMenuItemClickListener { revealCurrent(); true }; show()
        }
    }
    private fun chooseFile() { if(model.busy.value==null) picker.launch(arrayOf("*/*")) else message("Дождитесь завершения открытия или отмените копирование") }
    private fun recent() {
        val books=model.library.books()
        if(books.isEmpty()) { chooseFile(); return }
        MaterialAlertDialogBuilder(this).setTitle("Недавние справочники").setItems(books.map { "${it.name}\n${it.size/1024/1024} МБ" }.toTypedArray()) { _,which -> model.open(books[which]) }.setNegativeButton("Закрыть",null).setPositiveButton("Открыть файл") { _,_ -> chooseFile() }.show()
    }
    private fun chooseTheme() {
        val values=intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,AppCompatDelegate.MODE_NIGHT_NO,AppCompatDelegate.MODE_NIGHT_YES)
        val current=model.library.preferences.getInt("theme",values[0])
        MaterialAlertDialogBuilder(this).setTitle("Тема приложения").setSingleChoiceItems(arrayOf("Как в системе","Светлая","Тёмная"),values.indexOf(current)) { dialog,which ->
            savePosition(); model.library.preferences.edit().putInt("theme",values[which]).apply(); dialog.dismiss(); AppCompatDelegate.setDefaultNightMode(values[which])
        }.setMessage("Страницы сохраняют исходные цвета: это помогает читать схемы и таблицы.").setNegativeButton("Закрыть",null).show()
    }
    private fun chooseEncoding() {
        val b=book ?: return
        val labels=arrayOf("Автоматически","UTF-8","Кириллица Windows-1251","Кириллица DOS (CP866)","Кириллица KOI8-R","Западная Windows-1252","Японская Shift_JIS","Китайская GB18030")
        val values=arrayOf(null,"UTF-8","windows-1251","IBM866","KOI8-R","windows-1252","Shift_JIS","GB18030")
        val prefs=model.library.state(b)
        MaterialAlertDialogBuilder(this).setTitle("Кодировка страницы").setSingleChoiceItems(labels,values.indexOf(prefs.getString("encoding",null))) { dialog,which -> prefs.edit().putString("encoding",values[which]).apply(); dialog.dismiss(); web.reload() }.setNegativeButton("Закрыть",null).show()
    }
    private fun chooseTextSize() {
        val values=intArrayOf(80,100,120,150,180,200)
        MaterialAlertDialogBuilder(this).setTitle("Размер текста").setSingleChoiceItems(values.map { "$it%" }.toTypedArray(),values.indexOf(web.settings.textZoom)) { dialog,which -> web.settings.textZoom=values[which]; model.library.preferences.edit().putInt("textZoom",values[which]).apply(); dialog.dismiss() }.setNegativeButton("Закрыть",null).show()
    }
    private fun documentInfo() {
        val d=doc ?: return; val b=book ?: return
        MaterialAlertDialogBuilder(this).setTitle(d.metadata.title.ifBlank { b.name }).setMessage("${b.name}\n${b.size/1024/1024} МБ\n${d.archive.entries.size} ресурсов\n${d.toc.size} разделов\nОглавление: ${d.tocSource}\nКодировка по умолчанию: ${d.metadata.charset.name()}"+(d.warning?.let { "\n\n$it" } ?: "")).setPositiveButton("Закрыть",null).show()
    }
    private fun about() {
        MaterialAlertDialogBuilder(this).setTitle("CHM Reader ${BuildConfig.VERSION_NAME}").setMessage("Бесплатный офлайн-просмотрщик CHM.\nБез рекламы, подписок и аналитики.\n\nИсходники приложения: GPL-3.0-or-later.\nCHMLib © Jed Wing: LGPL-2.1-or-later.\njsoup: MIT. AndroidX и Material Components: Apache-2.0.\n\nJavaScript и ActiveX внутри справочников отключены.").setPositiveButton("Закрыть",null).setNeutralButton("Лицензии") { _,_ ->
            val text=assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
            val scroll=ScrollView(this); scroll.addView(TextView(this).apply { this.text=text; textSize=14f; setPadding(dp(20),dp(16),dp(20),dp(16)); setTextIsSelectable(true) })
            MaterialAlertDialogBuilder(this).setTitle("Лицензии компонентов").setView(scroll).setPositiveButton("Закрыть",null).show()
        }.show()
    }
    private fun confirmExternal(uri:Uri) {
        MaterialAlertDialogBuilder(this).setTitle("Открыть внешнюю ссылку?").setMessage(uri.toString()).setNegativeButton("Отмена",null).setPositiveButton("Открыть") { _,_ ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE)) }.onFailure { message("На устройстве нет приложения для этой ссылки") }
        }.show()
    }
    private fun showHighlight(query:String) {
        highlightTerms=Regex("[\\p{L}\\p{N}_]+").findAll(query).map { it.value }.distinct().toList()
        highlight=highlightTerms.firstOrNull() ?: query.trim(); if(highlight.isEmpty()) return
        highlightBar.visibility=View.VISIBLE; highlightLabel.text="Поиск: $highlight"
        highlightLabel.contentDescription=if(highlightTerms.size>1) "Выбрать слово для подсветки" else "Подсветка: $highlight"
        web.setFindListener { active,total,done -> if(done && highlight.isNotEmpty()) highlightLabel.text="$highlight · ${if(total==0) 0 else active+1} из $total" }
        web.findAllAsync(highlight)
    }
    private fun clearHighlight() { highlight=""; highlightTerms=emptyList(); highlightBar.visibility=View.GONE; web.clearMatches() }
    private fun message(text:String) { Snackbar.make(root,text,Snackbar.LENGTH_LONG).setTextMaxLines(5).show() }
    override fun onPause() { savePosition(); super.onPause() }
    override fun onSaveInstanceState(out:Bundle) { super.onSaveInstanceState(out); out.putBundle("web",Bundle().also { web.saveState(it) }); out.putString("book",book?.id); out.putString("highlight",highlight); savePosition() }
    override fun onDestroy() { web.stopLoading(); web.webViewClient=WebViewClient(); web.destroy(); super.onDestroy() }
}
