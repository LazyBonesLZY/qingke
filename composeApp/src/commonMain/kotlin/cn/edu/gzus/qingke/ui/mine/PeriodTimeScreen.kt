package cn.edu.gzus.qingke.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.normalizeClock
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.withFetchedPeriodTimes
import cn.edu.gzus.qingke.ui.components.ConfirmDialog
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 打字中途的半截值不算完整：带冒号时分钟必须两位（"08:3" 会被 normalizeClock 读成 08:03，
 * 一提交就把输入框改写成 08:03，用户再也打不出 08:30）；不带冒号要 3～4 位数字。
 */
private fun looksComplete(raw: String): Boolean {
    val text = raw.trim().replace('：', ':').replace(" ", "")
    return if (text.contains(':')) text.substringAfter(':').length == 2 else text.length in 3..4
}

/**
 * 两格都合法才写回；两格都清空则撤掉这一节的手动覆盖（回落到教务/内置值）。
 * 只有一边合法时什么都不做——打字中途的半截值不能写进去。
 */
private fun commitOrClear(start: String, end: String, onChange: (String?, String?) -> Unit) {
    val s = if (looksComplete(start)) normalizeClock(start) else null
    val e = if (looksComplete(end)) normalizeClock(end) else null
    when {
        // 起止反了（"10:00-09:00"）不当成有效输入，否则会原样上屏、
        // 让进度条和提醒拿到一个负长度区间。
        s != null && e != null -> if (s < e) onChange(s, e) else Unit
        start.isBlank() && end.isBlank() -> onChange(null, null)
    }
}

/** 给这一行的输入状态出一句提示；null 表示没问题或还没动过。 */
private fun rowProblem(start: String, end: String): String? {
    if (start.isBlank() && end.isBlank()) return null
    if (start.isBlank() || end.isBlank()) return "两格都填才生效；都清空就恢复默认"
    val s = if (looksComplete(start)) normalizeClock(start) else null
    val e = if (looksComplete(end)) normalizeClock(end) else null
    if (s == null || e == null) return "格式不对，填 24 小时制，例如 08:00"
    if (s >= e) return "结束要晚于开始，这次修改没生效"
    return null
}

/**
 * 左侧节次时间自定义。
 * 每块两格：开始 / 结束，都合法才写回；改过的块用用户值，其余继续走学校预设。
 */
@Composable
fun PeriodTimeScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val blocks = snapshot.resolved().periodBlocks
    val overrides = snapshot.settings.periodTimeOverrides
    val fetched = snapshot.profile.periodTimes
    // 不含手动覆盖的「默认值」，用于提示「清空后会回到什么」。
    val defaults = snapshot.settings.resolved().withFetchedPeriodTimes(fetched).periodBlocks
        .associate { it.label to "${it.start}–${it.end}" }
    // 点「清除我的修改」时自增，让各行输入框重新取初值；平时不跟着快照重置，免得打字时被改写。
    var resetToken by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val sourceName = if (fetched.isNotEmpty()) "教务时间" else "内置预设"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = Space.xl),
    ) {
        Spacer(Modifier.height(Space.s))
        Text(
            "改的是课表左边那一列的时间。填 24 小时制，例如 08:00 和 09:40；两格都合法就自动保存。",
            modifier = Modifier.padding(horizontal = Inset.page),
            color = Tone.muted(),
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            when {
                fetched.isNotEmpty() ->
                    "当前时间是从教务课表页自动抄下来的，共 ${fetched.size} 节。你改过的会盖在上面。"
                else ->
                    "还没从教务抄到节次时间。同步一次课表就会自动抄；抄不到就继续用内置预设，也可以自己改。"
            },
            modifier = Modifier.padding(horizontal = Inset.page),
            color = Tone.muted(),
            style = MiuixTheme.textStyles.footnote1,
        )
        SmallTitle(text = if (overrides.isEmpty()) "节次时间" else "节次时间 · 已改 ${overrides.size} 节")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            insideMargin = Inset.card,
        ) {
            blocks.forEachIndexed { index, block ->
                if (index > 0) Spacer(Modifier.height(Space.l))
                PeriodTimeRow(
                    label = block.label,
                    resetToken = resetToken,
                    customized = overrides.containsKey(block.label),
                    defaultRange = defaults[block.label].orEmpty(),
                    initialStart = overrides[block.label]?.substringBefore("-")?.trim()
                        ?: block.start,
                    initialEnd = overrides[block.label]?.substringAfter("-", "")?.trim()
                        ?: block.end,
                    onChange = { start, end ->
                        onUpdate { settings ->
                            val next = settings.periodTimeOverrides.toMutableMap()
                            if (start == null && end == null) {
                                next.remove(block.label)
                            } else {
                                next[block.label] = "${start.orEmpty()}-${end.orEmpty()}"
                            }
                            settings.copy(periodTimeOverrides = next)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(Space.l))
        Button(
            onClick = { confirmClear = true },
            enabled = overrides.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            minHeight = 44.dp,
        ) {
            if (overrides.isEmpty()) {
                Text("还没有改过任何一节")
            } else {
                Text("清除 ${overrides.size} 节修改，回到$sourceName", color = Tone.danger())
            }
        }
    }

    if (confirmClear) {
        // 一次撤掉所有手动时间，不可撤回，走统一确认框。
        ConfirmDialog(
            title = "清除全部修改？",
            message = "${overrides.size} 节手动填的时间会全部撤掉，回到$sourceName。",
            confirmText = "清除",
            onConfirm = {
                onUpdate { it.copy(periodTimeOverrides = emptyMap()) }
                resetToken++
            },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun PeriodTimeRow(
    label: String,
    resetToken: Int,
    customized: Boolean,
    defaultRange: String,
    initialStart: String,
    initialEnd: String,
    onChange: (String?, String?) -> Unit,
) {
    var startText by remember(label, resetToken) { mutableStateOf(initialStart) }
    var endText by remember(label, resetToken) { mutableStateOf(initialEnd) }
    // 本次进页后动过这一行，才给「已保存」回执；没动过只显示默认值说明。
    var touched by remember(label, resetToken) { mutableStateOf(false) }
    val problem = rowProblem(startText, endText)

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // 「中午」这类没有编号的块不能再套「第…节」，否则会变成「第 中午 节」。
                if (label.any { it.isDigit() }) "第 $label 节" else label,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (customized) StatusTag("已自定义")
        }
        Spacer(Modifier.height(Space.s))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            TextField(
                value = startText,
                onValueChange = { raw ->
                    startText = raw.take(5)
                    touched = true
                    commitOrClear(startText, endText, onChange)
                },
                label = "开始",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Text(
                "—",
                style = MiuixTheme.textStyles.body2,
                color = Tone.muted(),
            )
            TextField(
                value = endText,
                onValueChange = { raw ->
                    endText = raw.take(5)
                    touched = true
                    commitOrClear(startText, endText, onChange)
                },
                label = "结束",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        // 输入框下方只放一行状态：错误（危险色）> 已恢复 > 已保存（成功色）> 默认值说明。
        val danger = Tone.danger()
        val success = Tone.success()
        val muted = Tone.muted()
        val (hint, hintColor) = when {
            problem != null -> problem to danger
            // 两格清空 = 撤掉覆盖，给个回执，不然看着像把这一节删了。
            startText.isBlank() && endText.isBlank() ->
                (if (defaultRange.isNotBlank()) "已恢复默认 $defaultRange" else "已恢复默认") to muted
            touched && customized ->
                (if (defaultRange.isNotBlank()) "已保存 · 默认 $defaultRange" else "已保存") to success
            customized && defaultRange.isNotBlank() -> "默认 $defaultRange · 两格都清空即恢复" to muted
            else -> "" to muted
        }
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(hint, style = MiuixTheme.textStyles.footnote1, color = hintColor)
        }
    }
}
