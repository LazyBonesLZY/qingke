package cn.edu.gzus.qingke.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 首页大卡片要显示的东西；时间相关的都在调用方按同一时刻算好。 */
internal data class HeroModel(
    val title: String,
    /** 左上角状态：上课中 / 下一节 / 今天第一节。 */
    val status: String,
    /** 右上角：节次 · 起止时间。 */
    val periodLine: String,
    val facts: List<Pair<String, String>>,
    /** 倒数的数字段，例如 [2 小时, 15 分]；空表示不显示倒数（学校没作息）。 */
    val countdown: List<Pair<String, String>>,
    /** 倒数后缀：后上课 / 后下课。 */
    val countdownSuffix: String,
    val progress: Float?,
    val startClock: String,
    val endClock: String,
    val inClass: Boolean,
)

/** 分钟数拆成大数字 + 单位：38 → [38 分钟]，135 → [2 小时, 15 分]。 */
internal fun countdownParts(minutes: Int): List<Pair<String, String>> = when {
    minutes >= 60 && minutes % 60 == 0 -> listOf("${minutes / 60}" to "小时")
    minutes >= 60 -> listOf("${minutes / 60}" to "小时", "${minutes % 60}" to "分")
    else -> listOf("${minutes.coerceAtLeast(1)}" to "分钟")
}

@Composable
internal fun NextLessonHero(model: HeroModel, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val onPrimary = MiuixTheme.colorScheme.onPrimary
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = primary),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // 左上角压一层浅高光（linearGradient 默认左上→右下），纯色大卡不至于发闷。
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = model.status,
                        live = model.inClass,
                        background = onPrimary.copy(alpha = 0.18f),
                        color = onPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    if (model.periodLine.isNotBlank()) {
                        Text(
                            model.periodLine,
                            style = MiuixTheme.textStyles.footnote1,
                            color = onPrimary.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    model.title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    color = onPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = model.facts.filter { it.second.isNotBlank() }
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        facts.joinToString("  ·  ") { it.second },
                        style = MiuixTheme.textStyles.body2,
                        color = onPrimary.copy(alpha = 0.86f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (model.countdown.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        model.countdown.forEach { (value, unit) ->
                            Text(
                                value,
                                fontSize = 40.sp,
                                lineHeight = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = onPrimary,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                unit,
                                style = MiuixTheme.textStyles.body2,
                                color = onPrimary,
                                modifier = Modifier.padding(bottom = 6.dp, end = 6.dp),
                            )
                        }
                        Text(
                            model.countdownSuffix,
                            style = MiuixTheme.textStyles.body2,
                            color = onPrimary.copy(alpha = 0.78f),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                if (model.progress != null) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(onPrimary.copy(alpha = 0.24f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(model.progress.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(onPrimary),
                        )
                    }
                    if (model.startClock.isNotBlank() || model.endClock.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(model.startClock, style = MiuixTheme.textStyles.footnote1, fontSize = 11.sp, color = onPrimary.copy(alpha = 0.72f))
                            Spacer(Modifier.weight(1f))
                            Text(model.endClock, style = MiuixTheme.textStyles.footnote1, fontSize = 11.sp, color = onPrimary.copy(alpha = 0.72f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, live: Boolean, background: Color, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            Box(Modifier.width(6.dp).height(6.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Medium, color = color)
    }
}

internal enum class LessonState { Past, Now, Next, Later }

/** "HH:mm" → 当天第几分钟；格式不对返回 null。 */
internal fun clockToMinutes(clock: String): Int? {
    val h = clock.substringBefore(":").trim().toIntOrNull() ?: return null
    val m = clock.substringAfter(":", "").trim().toIntOrNull() ?: return null
    return h * 60 + m
}

/** 时间线左侧时间列宽：没作息的学校这里显示「11-12节」，48dp 会被截掉。 */
private val TimeCol = 52.dp

internal data class TimelineRow(
    val title: String,
    val detail: String,
    val startClock: String,
    val endClock: String,
    val state: LessonState,
    val accentKey: String,
    val onClick: () -> Unit,
)

/** 今天的课：一张卡里按时间排成一列，左边起止时间，色条对应课表里的课程色。 */
@Composable
internal fun TodayTimeline(rows: List<TimelineRow>) {
    val dark = MiuixTheme.colorScheme.background.luminanceIsDark()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(vertical = 6.dp),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                // 分隔线从课名那一列起：行内边距 16 + 时间列 + 色条左右 10×2 + 色条 4。
                Box(
                    Modifier
                        .padding(start = 16.dp + TimeCol + 24.dp, end = 16.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)),
                )
            }
            TimelineItem(row, accent = lessonAccent(row.accentKey, dark))
        }
    }
}

@Composable
private fun TimelineItem(row: TimelineRow, accent: Color) {
    val muted = MiuixTheme.colorScheme.onSurfaceContainerVariant
    val highlight = row.state == LessonState.Now || row.state == LessonState.Next
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(IntrinsicSize.Min)
            .alpha(if (row.state == LessonState.Past) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(TimeCol)) {
            Text(
                row.startClock,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = if (highlight) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.endClock.isNotBlank()) {
                Text(row.endClock, style = MiuixTheme.textStyles.footnote1, fontSize = 11.sp, color = muted, maxLines = 1)
            }
        }
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                row.title,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(row.detail, style = MiuixTheme.textStyles.footnote1, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (row.state) {
            LessonState.Now -> SmallTag("上课中", MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.onPrimary)
            LessonState.Next -> SmallTag("下一节", MiuixTheme.colorScheme.primary.copy(alpha = 0.14f), MiuixTheme.colorScheme.primary)
            LessonState.Past -> SmallTag("已结束", Color.Transparent, muted)
            LessonState.Later -> Unit
        }
    }
}

@Composable
private fun SmallTag(text: String, background: Color, color: Color) {
    Text(
        text,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MiuixTheme.textStyles.footnote1, fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
    )
}

internal data class ExamRow(
    val title: String,
    val detail: String,
    /** 右侧徽标：今天 / N 天 / 已考；空就不画。 */
    val badge: String,
    val urgent: Boolean,
    val past: Boolean,
)

/** 考试列表并进一张卡，右侧标出还有几天，三天内的标红。 */
@Composable
internal fun ExamList(rows: List<ExamRow>, more: Int, onClick: () -> Unit) {
    val muted = MiuixTheme.colorScheme.onSurfaceContainerVariant
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)))
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp).alpha(if (row.past) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.detail.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(row.detail, style = MiuixTheme.textStyles.footnote1, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.badge.isNotBlank()) {
                    val warn = Color(0xFFFF5C5C)
                    SmallTag(
                        row.badge,
                        if (row.urgent) warn.copy(alpha = 0.14f) else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                        if (row.urgent) warn else MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (more > 0) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)))
            Text(
                "查看全部 · 还有 $more 场",
                modifier = Modifier.padding(vertical = 10.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

// 顺序和课表的浅色块调色板一致（蓝、绿、橙、紫、红、青、靛、黄、粉、草绿），
// 同一门课在课表和首页是同一个色相，只是这里饱和度高、能当细色条用。
private val AccentsLight = listOf(
    Color(0xFF3482FF), Color(0xFF22A86B), Color(0xFFFF8A1F), Color(0xFF8A5CF6), Color(0xFFF2544B),
    Color(0xFF1FA9C2), Color(0xFF5568F0), Color(0xFFE0A800), Color(0xFFF0568F), Color(0xFF4FA83E),
)
private val AccentsDark = listOf(
    Color(0xFF6BA5FF), Color(0xFF4CC98E), Color(0xFFFFA455), Color(0xFFA889FF), Color(0xFFFF7D75),
    Color(0xFF4FCADF), Color(0xFF8592FF), Color(0xFFF2C440), Color(0xFFFF82AE), Color(0xFF79C96A),
)

private fun lessonAccent(key: String, dark: Boolean): Color {
    // 与课表 courseTint 相同的哈希取色方式。
    val hash = key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    val palette = if (dark) AccentsDark else AccentsLight
    return palette[hash % palette.size]
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
