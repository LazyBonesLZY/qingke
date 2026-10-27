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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Radius
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import cn.edu.gzus.qingke.ui.components.Type
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

// 本文件里仅有的几个「非刻度」尺寸：都是图形元素（细线、色条、圆点），不是间距。
/** 分割线粗细，与 ListGroup 一致。 */
private val Hairline = 0.5.dp
/** 时间线课程色条宽度。 */
private val AccentBar = 4.dp
/** 上课进度条高度。 */
private val ProgressBar = 6.dp
/** 「上课中」「待办」前的小圆点。 */
private val Dot = 6.dp

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
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = PaddingValues(),
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
                        listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    ),
                )
                .padding(Inset.card),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(text = model.status, live = model.inClass, color = onPrimary)
                    Spacer(Modifier.weight(1f))
                    if (model.periodLine.isNotBlank()) {
                        Text(
                            model.periodLine,
                            modifier = Modifier.padding(start = Space.s),
                            style = MiuixTheme.textStyles.footnote1,
                            color = onPrimary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(Space.m))
                Text(
                    model.title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = onPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = model.facts.filter { it.second.isNotBlank() }
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        facts.joinToString(" · ") { it.second },
                        style = MiuixTheme.textStyles.body2,
                        color = onPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (model.countdown.isNotEmpty()) {
                    Spacer(Modifier.height(Space.m))
                    // 大数字和单位按基线对齐，不再靠 padding(bottom) 硬凑。
                    Row(Modifier.fillMaxWidth()) {
                        model.countdown.forEachIndexed { index, (value, unit) ->
                            if (index > 0) Spacer(Modifier.width(Space.s))
                            Text(
                                value,
                                modifier = Modifier.alignByBaseline(),
                                fontSize = Type.display,
                                lineHeight = Type.displayLine,
                                fontWeight = FontWeight.SemiBold,
                                color = onPrimary,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                unit,
                                modifier = Modifier.alignByBaseline(),
                                style = MiuixTheme.textStyles.body2,
                                color = onPrimary,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(Space.s))
                        Text(
                            model.countdownSuffix,
                            modifier = Modifier.alignByBaseline(),
                            style = MiuixTheme.textStyles.body2,
                            color = onPrimary.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                }
                if (model.progress != null) {
                    Spacer(Modifier.height(Space.m))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ProgressBar)
                            .clip(Radius.pill)
                            .background(onPrimary.copy(alpha = 0.24f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(model.progress.coerceIn(0f, 1f))
                                .clip(Radius.pill)
                                .background(onPrimary),
                        )
                    }
                    if (model.startClock.isNotBlank() || model.endClock.isNotBlank()) {
                        Spacer(Modifier.height(Space.xs))
                        Row(Modifier.fillMaxWidth()) {
                            Text(model.startClock, style = MiuixTheme.textStyles.footnote1, fontSize = Type.tiny, color = onPrimary.copy(alpha = 0.72f))
                            Spacer(Modifier.weight(1f))
                            Text(model.endClock, style = MiuixTheme.textStyles.footnote1, fontSize = Type.tiny, color = onPrimary.copy(alpha = 0.72f))
                        }
                    }
                }
            }
        }
    }
}

/** 大卡左上角的状态胶囊：尺寸与 StatusTag 一致，多一个「上课中」的实心圆点；底色跟随卡片前景色。 */
@Composable
private fun StatusPill(text: String, live: Boolean, color: Color) {
    Row(
        Modifier
            .clip(Radius.pill)
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = Space.s, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            Box(Modifier.size(Dot).clip(Radius.pill).background(color))
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

/** 空状态之外的提醒卡：左侧警示点 + 一句结论 + 一句怎么办，右侧是动作。整卡可点。 */
@Composable
internal fun NoticeCard(title: String, detail: String, actionText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = Inset.card,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dot).clip(Radius.pill).background(Tone.warning()))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(detail, style = MiuixTheme.textStyles.footnote1, color = Tone.muted(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (actionText.isNotBlank()) {
                Spacer(Modifier.width(Space.m))
                StatusTag(actionText)
            }
        }
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
    GroupCard {
        rows.forEachIndexed { index, row ->
            // 分隔线从课名那一列起：行内边距 + 时间列 + 色条两侧间距 + 色条。
            if (index > 0) RowDivider(start = Inset.page + TimeCol + Space.m * 2 + AccentBar)
            TimelineItem(row, accent = lessonAccent(row.accentKey, dark))
        }
    }
}

@Composable
private fun TimelineItem(row: TimelineRow, accent: Color) {
    val muted = Tone.muted()
    val highlight = row.state == LessonState.Now || row.state == LessonState.Next
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .heightIn(min = Inset.rowMin)
            .padding(horizontal = Inset.page, vertical = RowPadV)
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
                Text(row.endClock, style = MiuixTheme.textStyles.footnote1, fontSize = Type.tiny, color = muted, maxLines = 1)
            }
        }
        Box(
            Modifier
                .padding(horizontal = Space.m)
                .width(AccentBar)
                .fillMaxHeight()
                .clip(Radius.pill)
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
                Spacer(Modifier.height(Space.xs / 2))
                Text(row.detail, style = MiuixTheme.textStyles.footnote1, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val tagModifier = Modifier.padding(start = Space.s)
        when (row.state) {
            LessonState.Now -> StatusTag("上课中", filled = true, modifier = tagModifier)
            LessonState.Next -> StatusTag("下一节", modifier = tagModifier)
            LessonState.Past -> StatusTag("已结束", color = muted, modifier = tagModifier)
            LessonState.Later -> Unit
        }
    }
}

/** 行内标签的语气，颜色统一从 [Tone] / 主题取，页面里不写色值。 */
internal enum class TagTone { Accent, Warning, Danger, Muted }

@Composable
private fun TagTone.color(): Color = when (this) {
    TagTone.Accent -> MiuixTheme.colorScheme.primary
    TagTone.Warning -> Tone.warning()
    TagTone.Danger -> Tone.danger()
    TagTone.Muted -> Tone.muted()
}

/** 首页分组卡里的一行：标题 + 若干行说明 + 右侧标签。尺寸与 ListGroup 的行一致。 */
internal data class BriefRow(
    val title: String,
    val lines: List<String> = emptyList(),
    val tag: String = "",
    val tagTone: TagTone = TagTone.Accent,
    /** 已经过去的条目（已考）整体淡化。 */
    val dimmed: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * 同类条目放进一张卡，行间细分割线；可选的底部「查看全部」行。
 * 和 ListGroup 的区别：右侧是 StatusTag 而不是大号徽标，说明可以多行（宿舍水电要分行列电/水）。
 */
@Composable
internal fun BriefGroup(rows: List<BriefRow>, footer: String = "", onFooter: (() -> Unit)? = null) {
    if (rows.isEmpty()) return
    GroupCard {
        rows.forEachIndexed { index, row ->
            if (index > 0) RowDivider(start = Inset.page)
            BriefItem(row)
        }
        if (footer.isNotBlank() && onFooter != null) {
            RowDivider(start = Inset.page)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onFooter)
                    .heightIn(min = Inset.rowMin - Space.s)
                    .padding(horizontal = Inset.page, vertical = RowPadV),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    footer,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Chevron()
            }
        }
    }
}

@Composable
private fun BriefItem(row: BriefRow) {
    val muted = Tone.muted()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
            .heightIn(min = Inset.rowMin)
            .padding(horizontal = Inset.page, vertical = RowPadV)
            .alpha(if (row.dimmed) 0.45f else 1f),
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
            row.lines.filter { it.isNotBlank() }.forEach { line ->
                Spacer(Modifier.height(Space.xs / 2))
                Text(line, style = MiuixTheme.textStyles.footnote1, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (row.tag.isNotBlank()) {
            Spacer(Modifier.width(Space.m))
            StatusTag(row.tag, color = row.tagTone.color())
        } else if (row.onClick != null) {
            Spacer(Modifier.width(Space.s))
            Chevron()
        }
    }
}

/** 行的上下内边距：配合 [Inset.rowMin]，与 ListGroup 的行同高。 */
private val RowPadV = 10.dp

/** 分组卡：上下留 [Space.xs]，左右不留，行自己带 [Inset.page]，点按反馈才能铺满整行。 */
@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = PaddingValues(vertical = Space.xs),
    ) {
        content()
    }
}

@Composable
private fun RowDivider(start: Dp) {
    Box(
        Modifier
            .padding(start = start, end = Inset.page)
            .fillMaxWidth()
            .height(Hairline)
            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)),
    )
}

@Composable
private fun Chevron() {
    Text("›", style = MiuixTheme.textStyles.title3, color = Tone.muted().copy(alpha = 0.6f))
}

// 顺序和课表的浅色块调色板一致（蓝、绿、橙、紫、红、青、靛、黄、粉、草绿），
// 同一门课在课表和首页是同一个色相，只是这里饱和度高、能当细色条用。
// 这是课程识别色，不是语义色，所以不走 Tone。
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
