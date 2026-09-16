package cn.edu.gzus.qingke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
val CanvasQuickHeight = 72.dp
val CanvasRowHeight = 88.dp
val CanvasHeroHeight = 120.dp
val CanvasProfileHeight = 100.dp
private val TileGap = 8.dp

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

@Composable
fun ScreenHeader(eyebrow: String, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (eyebrow.isNotBlank()) {
            ScrollText(eyebrow, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        }
        ScrollText(title, style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onBackground)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = CanvasHeroHeight),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (eyebrow.isNotBlank()) {
                Text(
                    eyebrow,
                    style = MiuixTheme.textStyles.footnote1,
                    color = onPrimary.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                title,
                style = MiuixTheme.textStyles.title2,
                color = onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val shown = facts.filter { it.second.isNotBlank() }
            shown.chunked(2).forEach { row ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MiuixTheme.textStyles.footnote1,
                                color = onPrimary.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                value,
                                style = MiuixTheme.textStyles.body2,
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
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(onPrimary.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(onPrimary),
                        )
                    }
                    if (progressLabel.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            progressLabel,
                            style = MiuixTheme.textStyles.footnote1,
                            color = onPrimary.copy(alpha = 0.86f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressLine(progress: Float) {
    val color = MiuixTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier.heightIn(min = CanvasRowHeight))
            .alpha(if (dimmed) 0.45f else 1f),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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
                if (wrap) {
                    Text(
                        title,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = align,
                        style = MiuixTheme.textStyles.title3,
                        color = if (tintTitle) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                    )
                } else {
                    ScrollText(
                        title,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = align,
                        style = MiuixTheme.textStyles.title3,
                        color = if (tintTitle) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                    )
                }
                summary.split('\n').filter { it.isNotBlank() }.forEach { line ->
                    Spacer(Modifier.height(4.dp))
                    if (wrap) {
                        Text(
                            line,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = align,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                    } else {
                        ScrollText(
                            line,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = align,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                    }
                }
            }
            if (progress != null) {
                if (wrap) Spacer(Modifier.height(12.dp))
                else Spacer(Modifier.height(8.dp))
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
    labelColor: Color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(fill),
            )
        }
        if (progressLabel.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                progressLabel,
                style = MiuixTheme.textStyles.footnote1,
                color = labelColor,
            )
        }
    }
}

@Composable
fun StatRow(items: List<Pair<String, String>>, onClicks: List<(() -> Unit)?> = emptyList()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(CanvasStatHeight),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        items.forEachIndexed { index, (value, label) ->
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
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
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    ScrollText(
                        label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun QuickRow(items: List<Triple<String, String, () -> Unit>>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(CanvasQuickHeight),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (title, summary, onClick) ->
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                onClick = onClick,
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ScrollText(
                        title,
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(2.dp))
                    ScrollText(
                        summary,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun FactsCard(
    fields: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    title: String = "",
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (title.isNotBlank()) {
            Text(title, style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onBackground)
        }
        fields.filter { it.second.isNotBlank() }.forEachIndexed { index, (label, value) ->
            if (index > 0 || title.isNotBlank()) Spacer(Modifier.height(10.dp))
            Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
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
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onBackground)
        rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Spacer(Modifier.height(10.dp))
            Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun FactGrid(
    fields: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val items = fields.filter { it.second.isNotBlank() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (label, value) ->
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
                        Spacer(Modifier.height(4.dp))
                        ScrollText(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        Spacer(Modifier.height(4.dp))
        Text(value.ifBlank { "—" }, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
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
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        pressFeedbackType = if (onClick != null) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = onClick,
    ) {
        Text(name.ifBlank { "未登录" }, style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onBackground)
        fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Spacer(Modifier.height(10.dp))
            Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(CanvasRowHeight),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            ScrollText(text, color = MiuixTheme.colorScheme.onSurfaceContainerVariant, style = MiuixTheme.textStyles.body2)
        }
    }
}
