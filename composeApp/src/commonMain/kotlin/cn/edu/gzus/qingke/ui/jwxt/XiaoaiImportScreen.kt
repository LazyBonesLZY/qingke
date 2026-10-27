package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.layout.Arrangement
import cn.edu.gzus.qingke.data.sectionTime
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
import cn.edu.gzus.qingke.ui.components.ListGroup
import cn.edu.gzus.qingke.ui.components.ListRow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

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
    // 输入框里的内容先本地认一遍，认不出就别让人点保存再等 toast
    val draftToken = remember(url) { parseXiaoaiToken(url) }
    val saved = url.trim() == snapshot.settings.xiaoaiEditUrl.trim() && token != null
    var commandCopied by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
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
                    "节次" to run {
                        val school = snapshot.resolved()
                        val known = (1..16).count { school.sectionTime(it) != null }
                        when {
                            known == 0 ->
                                "${school.jwxtName}没有公布作息，不会写入上课钟点，请在小爱里自己设时间"
                            known >= 16 -> "16 节 · ${school.jwxtName}作息"
                            else -> "$known 节 · ${school.jwxtName}作息（其余节次教务没给时间，不写入）"
                        }
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
        // 这句要原样敲进 vConsole，手抄容易错，点一下直接复制
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = {
                copyToClipboard(XiaoaiUserInfoCommand)
                commandCopied = true
            },
        ) {
            Text(
                XiaoaiUserInfoCommand,
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (commandCopied) "已复制，去 vConsole 的 Command 里粘贴" else "点这里复制这句",
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = url,
            onValueChange = { url = it },
            label = "UserInfo",
            singleLine = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val authHint = when {
            url.isBlank() -> ""
            draftToken == null -> "认不出这段内容：要么是整段 UserInfo JSON，要么是旧的 PC 编辑链接"
            !saved -> draftToken.authSummary() + "，点「保存授权」后生效"
            else -> draftToken.authSummary()
        }
        if (authHint.isNotBlank()) {
            Text(
                authHint,
                color = if (draftToken == null || draftToken.expired()) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainerVariant,
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
                enabled = !busy && draftToken != null && !draftToken.expired() && !saved,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 50.dp,
            ) {
                Text(if (saved) "授权已保存" else "保存授权")
            }
            Button(
                onClick = { confirmImport = true },
                enabled = !busy && converted.apiCourses.isNotEmpty() && token != null,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 50.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (busy) InfiniteProgressIndicator()
                Text(if (busy) "正在写入小爱" else "一键导入")
            }
            val importBlocker = when {
                converted.apiCourses.isEmpty() -> "没有能导入的课，先同步课表"
                token == null -> "先粘贴并保存授权，才能一键导入"
                else -> ""
            }
            if (importBlocker.isNotBlank() && !busy) {
                Text(
                    importBlocker,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (token != null) {
                Button(
                    onClick = { confirmClear = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 50.dp,
                ) {
                    Text("清除授权")
                }
            }
        }
        SmallTitle(text = "其他")
        ListGroup(
            listOf(
                ListRow(
                    title = "打开小爱课程表",
                    summary = "有独立 App 就打开，没有就进网页",
                    onClick = { openXiaoaiSchedule() },
                ),
                ListRow(
                    title = "复制官方 JSON",
                    summary = "courseInfos + timer，给适配器或自用",
                    onClick = {
                        copyToClipboard(encodeXiaoaiOfficial(converted.official))
                        onCopied()
                    },
                ),
                ListRow(
                    title = "小爱开放平台",
                    summary = "open-schedule-prod.ai.xiaomi.com",
                    onClick = { openUrl("https://open-schedule-prod.ai.xiaomi.com/") },
                ),
                ListRow(
                    title = "小爱网页课表",
                    summary = XIAOAI_SCHEDULE_HOME,
                    onClick = { openUrl(XIAOAI_SCHEDULE_HOME) },
                ),
            ),
        )
        Spacer(Modifier.height(16.dp))
    }

    if (confirmImport) {
        // 导入走 replaceCourses，会整表替换小爱里现有的课
        JwxtConfirmDialog(
            title = "导入小爱课程表",
            message = "会把 ${converted.apiCourses.size} 门课写进小爱课程表，替换那张表里现有的课程。手动在小爱里加的课会被覆盖。",
            confirmText = "确认导入",
            onConfirm = onImport,
            onDismiss = { confirmImport = false },
        )
    }
    if (confirmClear) {
        JwxtConfirmDialog(
            title = "清除授权",
            message = "清除后要重新去小爱复制一份 UserInfo 才能再导入。",
            confirmText = "清除",
            onConfirm = onClearUrl,
            onDismiss = { confirmClear = false },
        )
    }
}

private const val XiaoaiUserInfoCommand = "copy(JSON.stringify(await window.xiaoai.getUserInfo()))"
