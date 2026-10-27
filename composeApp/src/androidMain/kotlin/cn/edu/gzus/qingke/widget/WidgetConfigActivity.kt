package cn.edu.gzus.qingke.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import cn.edu.gzus.qingke.QingkeTheme
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.ScreenHeader
import cn.edu.gzus.qingke.ui.components.Space

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        setContent {
            QingkeTheme {
                // 边距与应用内页面一致：状态栏下留 xl，左右按 Inset.page 对齐（ScreenHeader 自带 16dp）。
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(vertical = Space.xl),
                ) {
                    ScreenHeader("", "小组件样式", "选择外观，放到桌面后可调整大小")
                    Spacer(Modifier.height(Space.l))
                    Column(
                        modifier = Modifier.padding(horizontal = Inset.page),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        InfoCard("纯色", "不透明，文字最清晰", height = null, onClick = { finishWith(id, WidgetStyle.Solid) })
                        InfoCard("模糊", "半透明雾面，透出壁纸", height = null, onClick = { finishWith(id, WidgetStyle.Blur) })
                        InfoCard("玻璃", "更通透，带一圈高光描边", height = null, onClick = { finishWith(id, WidgetStyle.Glass) })
                    }
                }
            }
        }
    }

    private fun finishWith(id: Int, style: WidgetStyle) {
        QingkeWidgets.saveStyle(this, id, style)
        val manager = AppWidgetManager.getInstance(this)
        QingkeWidgets.update(this, manager, id, QingkeWidgets.rangeOf(this, id))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
