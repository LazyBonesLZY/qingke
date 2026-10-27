package cn.edu.gzus.qingke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全应用统一的尺寸刻度。页面里不要再写零散的 6dp / 14dp / 18dp：
 * 间距只从 [Space] 取，卡片内边距只用 [Inset]，这样各页对齐和疏密才一致。
 */
object Space {
    /** 同一块内紧挨着的两行（标题和副标题）。 */
    val xs = 4.dp
    /** 卡片之间、行内元素之间。 */
    val s = 8.dp
    /** 卡片内的小分组之间。 */
    val m = 12.dp
    /** 页面左右边距、卡片内左右边距。 */
    val l = 16.dp
    /** 两个分区之间。 */
    val xl = 24.dp
}

object Inset {
    /** 页面左右留白，所有卡片、标题都按它对齐。 */
    val page = 16.dp
    /** 卡片内容内边距。 */
    val card = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    /** 列表行最小高度：单行 48，带副标题的行 56。 */
    val rowMin = 56.dp
    /** 只有标题、没有副标题的列表行最小高度。 */
    val rowMinCompact = 48.dp
    /** 空状态、提示类卡片：比普通卡片更松，让一句话有呼吸感。 */
    val emptyState = PaddingValues(horizontal = 20.dp, vertical = 24.dp)
}

object Radius {
    /** 标签、小按钮、徽标。 */
    val pill = RoundedCornerShape(50)
    /** 卡片内的小色块、进度条。 */
    val small = RoundedCornerShape(8.dp)
    /** 卡片内嵌的次级块（输入区、分段选择）。 */
    val medium = RoundedCornerShape(12.dp)
    /** 侧栏选中块、浮层面板。卡片本身用 miuix Card 的默认圆角，不要另设。 */
    val large = RoundedCornerShape(16.dp)
}

/**
 * 字号层级（miuix 的 textStyles 映射，括号里是实际字号）：
 * - 页面大标题：ScreenHeader（[Type.largeTitle] 28sp，SemiBold）
 * - 重点卡片标题（HeroCard 课程名）：title2（24sp）
 * - 大数字的单位旁值、统计值：title3（20sp）
 * - 卡片 / 行标题：main（17sp），需要强调时加 FontWeight.Medium
 * - 正文、说明：body2（14sp）
 * - 次要信息、时间、标签：footnote1（13sp）
 * - 大数字（倒数、绩点）：[Type.display]
 * 不要再自造 fontSize，除了 [Type] 里列的这几个。
 */
object Type {
    val display = 40.sp
    val displayLine = 44.sp
    /** 极小的辅助字（课表格子里的教室、时间轴、底栏标签）。 */
    val tiny = 11.sp
    /** 页面大标题，只给 ScreenHeader 用。 */
    val largeTitle = 28.sp
    val largeTitleLine = 34.sp
}

/** 语义色：成功 / 警告 / 危险，跟随深浅色。别在页面里再写 0xFF22A86B 之类的字面量。 */
object Tone {
    // 浅色值压暗过一档：白底上成功约 4.4:1、危险约 4.5:1（原来 3.3 / 3.9）；警告橙天生偏亮约 3.9:1，只用于加粗标签。
    @Composable
    fun success(): Color = if (isSystemInDarkTheme()) Color(0xFF4CC98E) else Color(0xFF178A57)

    @Composable
    fun warning(): Color = if (isSystemInDarkTheme()) Color(0xFFFFB454) else Color(0xFFC56A12)

    @Composable
    fun danger(): Color = if (isSystemInDarkTheme()) Color(0xFFFF7D75) else Color(0xFFD93A40)

    @Composable
    fun muted(): Color = MiuixTheme.colorScheme.onSurfaceContainerVariant

    /** 卡片内行间分割线。深色下 outline 本身已经很淡，不再叠透明度。 */
    @Composable
    fun divider(): Color =
        if (isSystemInDarkTheme()) MiuixTheme.colorScheme.outline else MiuixTheme.colorScheme.outline.copy(alpha = 0.6f)
}

/** 0.5dp 细分割线。[startIndent] 让线从文字起点开始，左侧留白与内容对齐。 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp, color: Color = Tone.divider()) {
    Box(modifier.padding(start = startIndent).fillMaxWidth().height(0.5.dp).background(color))
}

/** 可点行尾部的箭头，与 miuix ArrowPreference 同一个图标和尺寸，别再用「›」字符。 */
@Composable
fun RowChevron(modifier: Modifier = Modifier, color: Color = Tone.muted().copy(alpha = 0.6f)) {
    Icon(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        modifier = modifier.size(width = 10.dp, height = 16.dp),
        tint = color,
    )
}

/** 4dp 细进度条，两端圆头。卡片里、HeroCard 里都用它，不要再手搓两层 Box。 */
@Composable
fun ProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.18f),
    height: Dp = 4.dp,
) {
    Box(modifier.height(height).clip(Radius.pill).background(trackColor)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(Radius.pill)
                .background(color),
        )
    }
}

/** 小号胶囊标签：「上课中」「已结束」「3 天」「置顶」。填充色取前景色的 12% 透明度。 */
@Composable
fun StatusTag(text: String, color: Color = MiuixTheme.colorScheme.primary, filled: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(Radius.pill)
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MiuixTheme.textStyles.footnote1,
        fontSize = Type.tiny,
        fontWeight = FontWeight.Medium,
        color = if (filled) MiuixTheme.colorScheme.onPrimary else color,
        maxLines = 1,
    )
}

/**
 * 统一空状态：一句结论 + 一句为什么 / 怎么办 + 可选的动作。
 * 不要用一张固定高度的 InfoCard 表示「没有数据」。
 */
@Composable
fun EmptyState(
    title: String,
    detail: String = "",
    actionText: String = "",
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier.padding(horizontal = Inset.page),
) {
    Card(modifier = modifier.fillMaxWidth(), insideMargin = Inset.emptyState) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(detail, style = MiuixTheme.textStyles.body2, color = Tone.muted(), textAlign = TextAlign.Center)
            }
            if (actionText.isNotBlank() && onAction != null) {
                Spacer(Modifier.height(Space.m))
                Text(
                    actionText,
                    modifier = Modifier
                        .clip(Radius.pill)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 统一确认框。不可逆操作（提交、删除、清空、退出）都走它；
 * [destructive] 为 true 时确认按钮用危险色文字。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onBackground)
            if (message.isNotBlank()) {
                Spacer(Modifier.height(Space.s))
                Text(message, style = MiuixTheme.textStyles.body2, color = Tone.muted())
            }
            Spacer(Modifier.height(Space.l + Space.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), minHeight = 44.dp) { Text("取消") }
                Button(
                    onClick = {
                        onDismiss()
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp,
                    colors = if (destructive) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                ) {
                    if (destructive) Text(confirmText, color = Tone.danger()) else Text(confirmText)
                }
            }
        }
    }
}
