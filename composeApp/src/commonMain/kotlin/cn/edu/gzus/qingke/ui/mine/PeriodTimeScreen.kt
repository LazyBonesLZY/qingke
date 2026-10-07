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
import cn.edu.gzus.qingke.data.resolved
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 把 "8:5" / "08：05" / "0805" 之类都归一成 "HH:mm"，非法返回 null。 */
internal fun normalizeClock(raw: String): String? {
    val digits = raw.trim().replace('：', ':').replace(" ", "")
    if (digits.isEmpty()) return null
    val parts = when {
        digits.contains(':') -> digits.split(":", limit = 2)
        digits.length == 4 -> listOf(digits.substring(0, 2), digits.substring(2))
        digits.length == 3 -> listOf(digits.substring(0, 1), digits.substring(1))
        else -> return null
    }
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/**
 * 两格都合法才写回；两格都清空则撤掉这一节的手动覆盖（回落到教务/内置值）。
 * 只有一边合法时什么都不做——打字中途的半截值不能写进去。
 */
private fun commitOrClear(start: String, end: String, onChange: (String?, String?) -> Unit) {
    val s = normalizeClock(start)
    val e = normalizeClock(end)
    when {
        s != null && e != null -> onChange(s, e)
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
                fetched.isNotEmpty() -> "当前时间是从教务课表页自动抄下来的，共 ${fetched.size} 节。你改过的会盖在上面。"
                else -> "这所学校的课表页没带节次时间，现在用的是内置预设。你可以自己改。"
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
private fun PeriodTimeRow(    label: String,
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
