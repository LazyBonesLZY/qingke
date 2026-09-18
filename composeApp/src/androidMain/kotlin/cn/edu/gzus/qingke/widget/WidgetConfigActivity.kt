package cn.edu.gzus.qingke.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.QingkeTheme
import cn.edu.gzus.qingke.ui.components.InfoCard
import cn.edu.gzus.qingke.ui.components.ScreenHeader

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
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 24.dp)) {
                    Spacer(Modifier.height(16.dp))
                    ScreenHeader("", "小组件样式", "选一种外观。放上桌面后可以拉大小。")
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InfoCard("纯色", "不透明，字最清楚", height = null, onClick = { finishWith(id, WidgetStyle.Solid) })
                        InfoCard("模糊", "半透明雾面，透出壁纸", height = null, onClick = { finishWith(id, WidgetStyle.Blur) })
                        InfoCard("玻璃", "更透，带一圈高光边", height = null, onClick = { finishWith(id, WidgetStyle.Glass) })
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
