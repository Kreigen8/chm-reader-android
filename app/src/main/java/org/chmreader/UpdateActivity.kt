package org.chmreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.chmreader.ui.ReaderIcon
import org.chmreader.update.UpdateModel

class UpdateActivity : AppCompatActivity() {
    private lateinit var model:UpdateModel
    private lateinit var status:TextView
    private val permission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(packageManager.canRequestPackageInstalls()) install()
        else status.text="Установка не разрешена. APK сохранён; можно повторить установку позже."
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        model=ViewModelProvider(this)[UpdateModel::class.java]
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(MaterialColors.getColor(this,com.google.android.material.R.attr.colorSurface)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets -> val p=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()); v.setPadding(p.left,p.top,p.right,p.bottom); insets }
        root.addView(MaterialToolbar(this).apply { title="Обновления"; navigationIcon=ReaderIcon(this@UpdateActivity,"back"); navigationContentDescription="Назад к справочнику"; setNavigationOnClickListener { finish() } },LinearLayout.LayoutParams(-1,dp(64)))
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(24),dp(24),dp(24)) }
        content.addView(TextView(this).apply { text="CHM Reader ${BuildConfig.VERSION_NAME}"; textSize=22f; ViewCompat.setAccessibilityHeading(this,true) })
        content.addView(TextView(this).apply { text="Справочники читаются офлайн. Для обновления требуется интернет. Автоматических проверок нет."; textSize=16f; setPadding(0,dp(16),0,dp(24)) })
        status=TextView(this).apply { textSize=16f; setPadding(0,0,0,dp(16)); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }; content.addView(status)
        val progress=LinearProgressIndicator(this).apply { max=100 }; content.addView(progress,LinearLayout.LayoutParams(-1,dp(4)))
        val notes=TextView(this).apply { textSize=14f; setPadding(0,dp(16),0,dp(16)); setTextIsSelectable(true) }; content.addView(notes)
        val check=MaterialButton(this).apply { text="Проверить обновления"; setOnClickListener { model.check() } }; content.addView(check,LinearLayout.LayoutParams(-1,-2))
        val action=MaterialButton(this).apply { setOnClickListener { if(model.state.value?.apk!=null) install() else model.download() } }; content.addView(action,LinearLayout.LayoutParams(-1,-2))
        val cancel=MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text="Отменить"; setOnClickListener { model.cancel() } }; content.addView(cancel,LinearLayout.LayoutParams(-1,-2))
        val releases=MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text="Открыть релизы на GitHub"; setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(model.policy.releasesUrl))) }.onFailure { status.text="Не найден браузер для открытия GitHub" } } }; content.addView(releases,LinearLayout.LayoutParams(-1,-2))
        val scroll=android.widget.ScrollView(this).apply { addView(content) }; root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root); ViewCompat.requestApplyInsets(root)
        model.state.observe(this) { s ->
            status.text=s.status; notes.text=s.release?.notes.orEmpty(); notes.visibility=if(notes.text.isEmpty()) View.GONE else View.VISIBLE
            progress.visibility=if(s.busy) View.VISIBLE else View.GONE; progress.isIndeterminate=s.progress==null; s.progress?.let { progress.progress=it }
            check.isEnabled=!s.busy; cancel.visibility=if(s.busy) View.VISIBLE else View.GONE
            action.visibility=if(s.release!=null) View.VISIBLE else View.GONE; action.isEnabled=!s.busy
            action.text=if(s.apk!=null) "Установить обновление" else "Загрузить обновление"
        }
    }
    private fun install() {
        val apk=model.state.value?.apk ?: return
        if(!apk.exists()) { model.discardMissingApk(); return }
        if(!packageManager.canRequestPackageInstalls()) {
            status.text="Разрешите установку обновлений для CHM Reader в настройках Android."
            runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName"))) }.onFailure { status.text="Не удалось открыть настройки установки: ${it.message}" }
            return
        }
        runCatching {
            val uri=FileProvider.getUriForFile(this,"$packageName.updates",apk)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { status.text="Не удалось открыть установщик Android: ${it.message}" }
    }
}
