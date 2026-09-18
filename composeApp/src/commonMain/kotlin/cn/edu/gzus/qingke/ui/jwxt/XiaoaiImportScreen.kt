package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.XIAOAI_SCHEDULE_HOME
import cn.edu.gzus.qingke.data.School
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.school
import cn.edu.gzus.qingke.data.authSummary
import cn.edu.gzus.qingke.data.convertQingkeToXiaoai
import cn.edu.gzus.qingke.data.copyToClipboard
import cn.edu.gzus.qingke.data.encodeXiaoaiOfficial
import cn.edu.gzus.qingke.data.openUrl
import cn.edu.gzus.qingke.data.openXiaoaiSchedule
import cn.edu.gzus.qingke.data.parseXiaoaiToken
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.FactsCard
import cn.edu.gzus.qingke.ui.components.InfoCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun XiaoaiImportScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    busy: Boolean,
    onSaveUrl: (String) -> Unit,
    onClearUrl: () -> Unit,
    onImport: () -> Unit,
    onCopied: () -> Unit,
) {
    val converted = remember(snapshot.slots, snapshot.practices, snapshot.settings) {
        convertQingkeToXiaoai(snapshot)
    }
    val token = remember(snapshot.settings.xiaoaiEditUrl) {
        parseXiaoaiToken(snapshot.settings.xiaoaiEditUrl)
    }
    var url by remember(snapshot.settings.xiaoaiEditUrl) {
        mutableStateOf(snapshot.settings.xiaoaiEditUrl)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "把青课的课写进小爱课程表。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        if (converted.apiCourses.isEmpty()) {
            EmptyHint("还没有课可导。先到「我的」同步课表。")
        } else {
            FactsCard(
                title = "将导入 ${converted.apiCourses.size} 门",
                fields = listOf(
                    "节次" to if (snapshot.resolved().hasPeriodClock) {
                        if (snapshot.school() == School.Gzus) "16 节 · 广软作息" else "16 节 · 正方常见作息，学校没公布就不要开"
                    } else {
                        "${snapshot.resolved().jwxtName}没有公布作息，不会写入上课钟点，请在小爱里自己设时间"
                    },
                    "学期起始" to snapshot.settings.termStart.ifBlank { "还没设第1周周一" },
                    "总周数" to converted.official.timer.totalWeek.toString(),
                    "周末" to if (converted.official.timer.showWeekend) "有课，会显示周六日" else "没有周末课",
                    "跳过" to if (converted.skipped == 0) "无" else "${converted.skipped} 条（缺周次/节次，或实践课）",
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        SmallTitle(text = "授权")
        Text(
            "小爱课程表 → 头像 → 设置滑到最底下空白处连点 5 次 → 打开 vConsole。在 Command 里输入下面这句，把返回的 JSON 整段贴过来。旧的 PC 编辑链接也能用。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "copy(JSON.stringify(await window.xiaoai.getUserInfo()))",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.footnote1,
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = url,
            onValueChange = { url = it },
            label = "UserInfo",
            singleLine = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (token != null) {
            Text(
                token.authSummary(),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onSaveUrl(url) },
                enabled = !busy && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                minHeight = 50.dp,
            ) {
                Text("保存授权")
            }
            Button(
                onClick = onImport,
                enabled = !busy && converted.apiCourses.isNotEmpty() && token != null,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 50.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (busy) InfiniteProgressIndicator()
                Text(if (busy) "正在写入小爱" else "一键导入")
            }
            if (token != null) {
                Button(
                    onClick = onClearUrl,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 50.dp,
                ) {
                    Text("清除授权")
                }
            }
        }
        SmallTitle(text = "其他")
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoCard(
                title = "打开小爱课程表",
                summary = "有独立 App 就打开，没有就进网页",
                onClick = { openXiaoaiSchedule() },
            )
            InfoCard(
                title = "复制官方 JSON",
                summary = "courseInfos + timer，给适配器或自用",
                onClick = {
                    copyToClipboard(encodeXiaoaiOfficial(converted.official))
                    onCopied()
                },
            )
            InfoCard(
                title = "小爱开放平台",
                summary = "open-schedule-prod.ai.xiaomi.com",
                onClick = { openUrl("https://open-schedule-prod.ai.xiaomi.com/") },
            )
            InfoCard(
                title = "小爱网页课表",
                summary = XIAOAI_SCHEDULE_HOME,
                onClick = { openUrl(XIAOAI_SCHEDULE_HOME) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
