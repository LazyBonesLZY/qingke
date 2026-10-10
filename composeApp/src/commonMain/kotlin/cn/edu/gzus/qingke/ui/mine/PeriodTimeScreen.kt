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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.normalizeClock
import cn.edu.gzus.qingke.data.resolved
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 两格都合法才写回；两格都清空则撤掉这一节的手动覆盖（回落到教务/内置值）。
 * 只有一边合法时什么都不做——打字中途的半截值不能写进去。
 */
private fun commitOrClear(start: String, end: String, onChange: (String?, String?) -> Unit) {
    val s = normalizeClock(start)
    val e = normalizeClock(end)
    when {
        // 起止反了（"10:00-09:00"）不当成有效输入，否则会原样上屏、
        // 让进度条和提醒拿到一个负长度区间。
        s != null && e != null -> if (s < e) onChange(s, e) else Unit
        start.isBlank() && end.isBlank() -> onChange(null, null)
    }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "改的是课表左边那一列的时间。填 24 小时制，例如 08:00 和 09:40；两格都合法才生效。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                fetched.isNotEmpty() ->
                    "当前时间是从教务课表页自动抄下来的，共 ${fetched.size} 节。你改过的会盖在上面。"
                else ->
                    "还没从教务抄到节次时间。同步一次课表就会自动抄；抄不到就继续用内置预设，也可以自己改。"
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.footnote1,
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                PeriodTimeRow(
                    label = block.label,
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
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onUpdate { it.copy(periodTimeOverrides = emptyMap()) } },
            enabled = overrides.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minHeight = 44.dp,
        ) { Text(if (fetched.isNotEmpty()) "清除我的修改，回到教务时间" else "全部恢复内置预设") }
    }
}

@Composable
private fun PeriodTimeRow(
    label: String,
    initialStart: String,
    initialEnd: String,
    onChange: (String?, String?) -> Unit,
) {
    var startText by remember(label, initialStart) { mutableStateOf(initialStart) }
    var endText by remember(label, initialEnd) { mutableStateOf(initialEnd) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            // 「中午」这类没有编号的块不能再套「第…节」，否则会变成「第 中午 节」。
            if (label.any { it.isDigit() }) "第 $label 节" else label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = startText,
                onValueChange = { raw ->
                    startText = raw.take(5)
                    commitOrClear(startText, endText, onChange)
                },
                label = "开始",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Text(
                "—",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            TextField(
                value = endText,
                onValueChange = { raw ->
                    endText = raw.take(5)
                    commitOrClear(startText, endText, onChange)
                },
                label = "结束",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
