package cn.edu.gzus.qingke.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

val CanvasStatHeight = 72.dp
val CanvasRowHeight = 88.dp
val CanvasHeroHeight = 120.dp
private val TileGap = Space.s

/** 列表行徽标的最大宽度：再长就省略，保证左侧标题至少有一大半宽度。 */
private val BadgeMaxWidth = 120.dp

@Composable
fun ScrollText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MiuixTheme.textStyles.main,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 32.dp),
        color = color,
        style = style,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        lineHeight = lineHeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

/**
 * 页面大标题，仿系统设置页：28sp 半粗标题 + 一行灰色说明。
 * [trailing] 放在标题同一行右侧（例如一个小按钮），不传就没有。
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val muted = Tone.muted()
    Column(modifier.fillMaxWidth().padding(horizontal = Inset.page)) {
        if (eyebrow.isNotBlank()) {
            Text(
                eyebrow,
                style = MiuixTheme.textStyles.footnote1,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.title2,
                fontSize = Type.largeTitle,
                lineHeight = Type.largeTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailing != null) {
                Spacer(Modifier.width(Space.m))
                trailing()
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                subtitle,
                style = MiuixTheme.textStyles.body2,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HeroCard(
    title: String,
    eyebrow: String = "",
    facts: List<Pair<String, String>> = emptyList(),
    progress: Float? = null,
    progressLabel: String = "",
    onClick: () -> Unit,
) {
    val onPrimary = MiuixTheme.colorScheme.onPrimary
    // 主色底上的次要字：浅色主色本身偏亮，透明度太低会糊，统一压在 0.82 以上。
    val onPrimarySoft = onPrimary.copy(alpha = 0.82f)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page).heightIn(min = CanvasHeroHeight),
        insideMargin = Inset.card,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (eyebrow.isNotBlank()) {
                Text(
                    eyebrow,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = onPrimarySoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xs))
            }
            Text(
                title,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Medium,
                color = onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val shown = facts.filter { it.second.isNotBlank() }
            shown.chunked(2).forEach { row ->
                Spacer(Modifier.height(Space.m))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MiuixTheme.textStyles.footnote1,
                                color = onPrimarySoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                value,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.Medium,
                                color = onPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(Space.m))
                CardProgressRow(
                    progress = progress,
                    progressLabel = progressLabel,
                    track = onPrimary.copy(alpha = 0.24f),
                    fill = onPrimary,
                    labelColor = onPrimarySoft,
                )
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    height: Dp? = CanvasRowHeight,
    tintTitle: Boolean = false,
    dimmed: Boolean = false,
    center: Boolean = false,
    progress: Float? = null,
    progressLabel: String = "",
    onClick: (() -> Unit)? = null,
) {
    val wrap = height == null
    val align = if (center) TextAlign.Center else TextAlign.Start
    val titleColor = if (tintTitle) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground
    val muted = Tone.muted()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier.heightIn(min = CanvasRowHeight))
            .alpha(if (dimmed) 0.45f else 1f),
        insideMargin = Inset.card,
        pressFeedbackType = if (onClick != null) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = onClick,
    ) {
        Column(
            modifier = if (wrap) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
            verticalArrangement = if (progress != null && !wrap) Arrangement.SpaceBetween else Arrangement.Center,
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                // 卡片标题按规范用 main + Medium，不再用 title3，免得和页面大标题抢层级。
                if (wrap) {
                    Text(
                        title,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = align,
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.Medium,
                        color = titleColor,
                    )
                } else {
                    ScrollText(
                        title,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = align,
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.Medium,
                        color = titleColor,
                    )
                }
                summary.split('\n').filter { it.isNotBlank() }.forEach { line ->
                    Spacer(Modifier.height(Space.xs))
                    if (wrap) {
                        Text(
                            line,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = align,
                            style = MiuixTheme.textStyles.body2,
                            color = muted,
                        )
                    } else {
                        ScrollText(
                            line,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = align,
                            style = MiuixTheme.textStyles.body2,
                            color = muted,
                        )
                    }
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(if (wrap) Space.m else Space.s))
                CardProgressRow(progress = progress, progressLabel = progressLabel)
            }
        }
    }
}

@Composable
private fun CardProgressRow(
    progress: Float,
    progressLabel: String,
    track: Color = MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
    fill: Color = MiuixTheme.colorScheme.primary,
    labelColor: Color = Tone.muted(),
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProgressTrack(progress = progress, modifier = Modifier.weight(1f), color = fill, trackColor = track)
        if (progressLabel.isNotBlank()) {
            Spacer(Modifier.width(Space.s))
            Text(
                progressLabel,
                style = MiuixTheme.textStyles.footnote1,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun StatRow(items: List<Pair<String, String>>, onClicks: List<(() -> Unit)?> = emptyList()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page).height(CanvasStatHeight),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        items.forEachIndexed { index, (value, label) ->
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                insideMargin = PaddingValues(horizontal = Space.xs, vertical = Space.s),
                pressFeedbackType = if (onClicks.getOrNull(index) != null) PressFeedbackType.Sink else PressFeedbackType.None,
                onClick = onClicks.getOrNull(index),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ScrollText(
                        value,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(Space.xs))
                    ScrollText(
                        label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = Tone.muted(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 卡片标题：main + Medium，FactsCard / LabeledCard 共用。 */
@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.main,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onBackground,
    )
}

/** 一对「灰色小标签 + 正文值」，FactsCard / LabeledCard / ProfileCard 共用，保证三处行距一致。 */
@Composable
private fun LabeledValue(label: String, value: String) {
    Text(label, style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
    Spacer(Modifier.height(Space.xs))
    Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
}

@Composable
fun FactsCard(
    fields: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    title: String = "",
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = Inset.card,
    ) {
        if (title.isNotBlank()) CardTitle(title)
        fields.filter { it.second.isNotBlank() }.forEachIndexed { index, (label, value) ->
            if (index > 0 || title.isNotBlank()) Spacer(Modifier.height(Space.m))
            LabeledValue(label, value)
        }
    }
}

@Composable
fun LabeledCard(
    title: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = Inset.card,
    ) {
        CardTitle(title)
        rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Spacer(Modifier.height(Space.m))
            LabeledValue(label, value)
        }
    }
}

@Composable
fun FactGrid(
    fields: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val items = fields.filter { it.second.isNotBlank() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TileGap)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                row.forEach { (label, value) ->
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        insideMargin = PaddingValues(Space.m),
                    ) {
                        Text(
                            label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = Tone.muted(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Space.xs))
                        ScrollText(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ProfileCard(
    name: String,
    fields: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = Inset.card,
        pressFeedbackType = if (onClick != null) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = onClick,
    ) {
        // 名字比卡片标题高一级（title3），但低于页面大标题，避免两个「大字」挤在一屏顶部。
        Text(
            name.ifBlank { "未登录" },
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Spacer(Modifier.height(Space.m))
            LabeledValue(label, value)
        }
    }
}

/** 一句话的轻提示卡。需要标题 + 动作的空状态请用 [EmptyState]。 */
@Composable
fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
        insideMargin = Inset.emptyState,
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            style = MiuixTheme.textStyles.body2,
            color = Tone.muted(),
            textAlign = TextAlign.Center,
        )
    }
}

/** 列表行右侧徽标的语气：成绩好坏、提醒、普通计数。 */
enum class BadgeTone { Neutral, Accent, Good, Warn, Muted }

data class ListRow(
    val title: String,
    val summary: String = "",
    /** 右侧醒目的值：成绩、条数、状态；空就画箭头（可点时）。 */
    val badge: String = "",
    val tone: BadgeTone = BadgeTone.Neutral,
    val onClick: (() -> Unit)? = null,
)

/**
 * 一组同类条目放进一张卡，行间细分割线。以前每项一张 88dp 的独立卡，
 * 一屏只放得下六七项，分数之类的关键值也埋在小字里。
 */
@Composable
fun ListGroup(rows: List<ListRow>, modifier: Modifier = Modifier.padding(horizontal = Inset.page)) {
    if (rows.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(vertical = Space.xs)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                // 分割线从文字起点开始、通到卡片右缘，和系统设置列表一致。
                HairlineDivider(startIndent = Inset.page)
            }
            ListRowItem(row)
        }
    }
}

/** 纯数字类徽标（分数、绩点、百分比）才用大号字；「未出分」「3 条待办」这类文字走正文字号，不喧宾夺主。 */
private fun String.isNumericBadge(): Boolean =
    isNotEmpty() && length <= 6 && any { it.isDigit() } &&
        all { it.isDigit() || it == '.' || it == '+' || it == '-' || it == '%' || it == '/' }

@Composable
private fun ListRowItem(row: ListRow) {
    val muted = Tone.muted()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
            .heightIn(min = if (row.summary.isBlank()) Inset.rowMinCompact else Inset.rowMin)
            .padding(horizontal = Inset.page, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MiuixTheme.textStyles.main,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.summary.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(row.summary, style = MiuixTheme.textStyles.footnote1, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (row.badge.isNotBlank()) {
            Spacer(Modifier.width(Space.m))
            val color = when (row.tone) {
                BadgeTone.Neutral -> MiuixTheme.colorScheme.onBackground
                BadgeTone.Accent -> MiuixTheme.colorScheme.primary
                BadgeTone.Good -> Tone.success()
                BadgeTone.Warn -> Tone.danger()
                BadgeTone.Muted -> muted
            }
            val big = row.tone != BadgeTone.Muted && row.badge.isNumericBadge()
            Text(
                row.badge,
                // Row 先量非 weight 的子项：不限宽的话长徽标会把标题挤没。
                modifier = Modifier.widthIn(max = BadgeMaxWidth),
                style = when {
                    big -> MiuixTheme.textStyles.title3
                    row.tone == BadgeTone.Muted -> MiuixTheme.textStyles.footnote1
                    else -> MiuixTheme.textStyles.body2
                },
                fontWeight = when {
                    big -> FontWeight.SemiBold
                    row.tone == BadgeTone.Muted -> FontWeight.Normal
                    else -> FontWeight.Medium
                },
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.onClick != null) {
            // 可点的行一律带箭头，有徽标时箭头跟在徽标后面，和系统设置「值 + ›」一致。
            Spacer(Modifier.width(Space.s))
            RowChevron()
        }
    }
}
