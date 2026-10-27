package cn.edu.gzus.qingke.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.edu.gzus.qingke.QingkeBackgroundPickerButton
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.BACKGROUND_CUSTOM
import cn.edu.gzus.qingke.data.BACKGROUND_SYSTEM
import cn.edu.gzus.qingke.data.BACKGROUND_WALLPAPER
import cn.edu.gzus.qingke.ui.components.ConfirmDialog
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Radius
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.Tone
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform

/**
 * KernelSU/Miuix 风格的主题设置页。
 * 所有改动经 [onUpdate] 写回 AppSettings，App 层重组即生效。
 */
@Composable
fun ThemeSettingsScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val wallpaperSupported = platform() != Platform.Desktop

    var showUiScaleDialog by remember { mutableStateOf(false) }
    var showTintDialog by remember { mutableStateOf(false) }
    var showAlphaDialog by remember { mutableStateOf(false) }
    var showInkDialog by remember { mutableStateOf(false) }
    var confirmClearImage by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        item { Spacer(Modifier.height(Space.s)) }
        item { SmallTitle(text = "背景") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Inset.page),
            ) {
                BackgroundModeOption(
                    label = "跟随系统",
                    summary = "应用默认背景色，自动适配深色模式",
                    selected = settings.backgroundMode == BACKGROUND_SYSTEM,
                    onClick = { onUpdate { it.copy(backgroundMode = BACKGROUND_SYSTEM) } },
                )
                if (wallpaperSupported) {
                    BackgroundModeOption(
                        label = "手机壁纸",
                        summary = "用当前系统壁纸当背景",
                        selected = settings.backgroundMode == BACKGROUND_WALLPAPER,
                        onClick = { onUpdate { it.copy(backgroundMode = BACKGROUND_WALLPAPER) } },
                    )
                }
                BackgroundModeOption(
                    label = "自定义图片",
                    summary = "从相册里挑一张",
                    selected = settings.backgroundMode == BACKGROUND_CUSTOM,
                    onClick = { onUpdate { it.copy(backgroundMode = BACKGROUND_CUSTOM) } },
                )
                if (settings.backgroundMode == BACKGROUND_CUSTOM) {
                    CustomBackgroundActions(
                        uri = settings.backgroundImageUri,
                        onPicked = { uri ->
                            onUpdate { it.copy(backgroundImageUri = uri, backgroundMode = BACKGROUND_CUSTOM) }
                        },
                        onClear = { confirmClearImage = true },
                    )
                }
            }
        }
        item { SmallTitle(text = "效果") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Inset.page),
            ) {
                SwitchPreference(
                    title = "底栏模糊",
                    summary = if (settings.blurEnabled) "底栏对下层内容做实时模糊" else "关闭后底栏用半透明纯色",
                    checked = settings.blurEnabled,
                    onCheckedChange = { v -> onUpdate { it.copy(blurEnabled = v) } },
                )
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = if (settings.floatingBottomBar) "圆角胶囊悬浮在内容上方" else "贴底常驻，不占内容区",
                    checked = settings.floatingBottomBar,
                    onCheckedChange = { v -> onUpdate { it.copy(floatingBottomBar = v) } },
                )
                ArrowPreference(
                    title = "界面缩放",
                    summary = "全局字号与间距同步缩放",
                    endActions = { ValueText(uiScaleLabel(settings.uiScale)) },
                    onClick = { showUiScaleDialog = true },
                )
            }
        }
        item { SmallTitle(text = "课表") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Inset.page),
            ) {
                SwitchPreference(
                    title = "单屏显示整周",
                    summary = if (settings.compactTimetable) "缩小行高与字号，一周尽量放一屏" else "恢复宽松行高，课多时可上下滑",
                    checked = settings.compactTimetable,
                    onCheckedChange = { v -> onUpdate { it.copy(compactTimetable = v) } },
                )
            }
        }
        item { SmallTitle(text = "课程色块") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Inset.page),
            ) {
                // 先看效果再改：三个示例色块按当前底色 / 透明度 / 字色实时渲染。
                CourseTintPreview(settings)
                ChoiceRow(
                    label = "色块底色",
                    summary = "所有课程统一用一种底色，或按课程自动配色",
                    value = tintLabel(settings.courseTintHex),
                    swatch = tintSwatch(settings.courseTintHex),
                    selected = false,
                    onClick = { showTintDialog = true },
                )
                ChoiceRow(
                    label = "色块透明度",
                    summary = "越低越透出背景",
                    value = "${(settings.courseTintAlpha * 100).toInt()}%",
                    swatch = null,
                    selected = false,
                    onClick = { showAlphaDialog = true },
                )
                ChoiceRow(
                    label = "文字颜色",
                    summary = "色块上课程名的颜色",
                    value = inkLabel(settings.courseInkMode),
                    swatch = inkSwatch(settings.courseInkMode),
                    selected = false,
                    onClick = { showInkDialog = true },
                )
            }
        }
        item { Spacer(Modifier.height(Space.xl)) }
    }

    if (confirmClearImage) {
        // 清掉后要重新去相册挑，走统一确认框。
        ConfirmDialog(
            title = "清除自定义图片？",
            message = "背景会恢复为跟随系统，之后想用需要重新选择图片。",
            confirmText = "清除",
            // 清掉图片后还停在「自定义图片」只会得到空背景，顺手退回跟随系统。
            onConfirm = { onUpdate { it.copy(backgroundImageUri = "", backgroundMode = BACKGROUND_SYSTEM) } },
            onDismiss = { confirmClearImage = false },
            destructive = true,
        )
    }

    if (showUiScaleDialog) {
        ChoiceDialog(title = "界面缩放", onDismiss = { showUiScaleDialog = false }) {
            UiScaleChoices.forEach { choice ->
                ChoiceRow(
                    label = uiScaleLabel(choice.value),
                    summary = choice.summary,
                    swatch = null,
                    selected = kotlin.math.abs(settings.uiScale - choice.value) < 0.004f,
                    onClick = {
                        onUpdate { it.copy(uiScale = choice.value) }
                        showUiScaleDialog = false
                    },
                )
            }
        }
    }

    if (showTintDialog) {
        ChoiceDialog(title = "色块底色", onDismiss = { showTintDialog = false }) {
            CourseTintChoices.forEach { choice ->
                ChoiceRow(
                    label = choice.label,
                    summary = if (choice.hex.isEmpty()) "按课程自动配色" else choice.hex,
                    swatch = choice.swatch,
                    selected = normalizeTintHex(settings.courseTintHex) == normalizeTintHex(choice.hex),
                    onClick = {
                        onUpdate { it.copy(courseTintHex = choice.hex) }
                        showTintDialog = false
                    },
                )
            }
        }
    }

    if (showAlphaDialog) {
        ChoiceDialog(title = "色块透明度", onDismiss = { showAlphaDialog = false }) {
            CourseAlphaChoices.forEach { choice ->
                ChoiceRow(
                    label = "${(choice.value * 100).toInt()}%",
                    summary = choice.summary,
                    swatch = null,
                    selected = kotlin.math.abs(settings.courseTintAlpha - choice.value) < 0.004f,
                    onClick = {
                        onUpdate { it.copy(courseTintAlpha = choice.value) }
                        showAlphaDialog = false
                    },
                )
            }
        }
    }

    if (showInkDialog) {
        ChoiceDialog(title = "文字颜色", onDismiss = { showInkDialog = false }) {
            CourseInkChoices.forEach { choice ->
                ChoiceRow(
                    label = choice.label,
                    summary = choice.summary,
                    swatch = choice.swatch,
                    selected = settings.courseInkMode == choice.mode,
                    onClick = {
                        onUpdate { it.copy(courseInkMode = choice.mode, courseInkHex = choice.hex) }
                        showInkDialog = false
                    },
                )
            }
        }
    }
}

/** 设置行右侧的当前值：次要色正文，和系统设置的写法一致。 */
@Composable
private fun ValueText(text: String) {
    Text(
        text,
        modifier = Modifier.padding(end = Space.xs),
        style = MiuixTheme.textStyles.body2,
        color = Tone.muted(),
        maxLines = 1,
    )
}

/** 单选的勾：全页同一种写法。 */
@Composable
private fun CheckMark() {
    Text(
        "✓",
        modifier = Modifier.padding(end = Space.xs),
        style = MiuixTheme.textStyles.main,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
    )
}

@Composable
private fun BackgroundModeOption(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = label,
        summary = summary,
        endActions = { if (selected) CheckMark() },
        onClick = onClick,
    )
}

@Composable
private fun CustomBackgroundActions(
    uri: String,
    onPicked: (String) -> Unit,
    onClear: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Inset.page, vertical = Space.s),
    ) {
        QingkeBackgroundPickerButton(
            label = if (uri.isBlank()) "选择图片" else "重新选择图片",
            onPicked = onPicked,
        )
    }
    if (uri.isBlank()) {
        Text(
            "还没选图片，选好之前先显示默认背景",
            modifier = Modifier.padding(start = Inset.page, end = Inset.page, bottom = Space.m),
            style = MiuixTheme.textStyles.footnote1,
            color = Tone.muted(),
        )
    } else {
        // 原来摘要直接露出 content:// 这类长串路径，对用户没有意义。
        ArrowPreference(
            title = "清除自定义图片",
            summary = "恢复为跟随系统背景",
            onClick = onClear,
        )
    }
}

/**
 * 课程色块预览：口径与 TimetableScreen 的 resolveTint / resolveInk 一致
 * （自动配色取默认调色板前三色，透明度下限 0.2，自动字色按叠在卡片底色上的合成亮度判断）。
 */
@Composable
private fun CourseTintPreview(settings: AppSettings) {
    val dark = isSystemInDarkTheme()
    val backdrop = MiuixTheme.colorScheme.surfaceContainer
    val fixed = parsePreviewHex(settings.courseTintHex)
    val autoTints = if (dark) PreviewTintsDark else PreviewTintsLight
    val alpha = settings.courseTintAlpha.coerceIn(0.2f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = Inset.page).padding(top = Space.m, bottom = Space.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PreviewNames.forEachIndexed { index, name ->
                val tint = (fixed ?: autoTints[index]).copy(alpha = alpha)
                val autoInk = if (tint.compositeOver(backdrop).luminance() > 0.6f) PreviewInkLight else PreviewInkDark
                val ink = when (settings.courseInkMode) {
                    "light" -> PreviewInkLight
                    "dark" -> PreviewInkDark
                    "custom" -> parsePreviewHex(settings.courseInkHex) ?: autoInk
                    else -> autoInk
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(Radius.small)
                        .background(tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text("预览 · 课表里的色块会是这个样子", style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
    }
}

private val PreviewNames = listOf("高数", "英语", "体育")

/** 与 TimetableScreen 的 CourseTintsLight / CourseTintsDark 前三色一致。 */
private val PreviewTintsLight = listOf(Color(0xFFDCE9FF), Color(0xFFD8F3E4), Color(0xFFFFE6CC))
private val PreviewTintsDark = listOf(Color(0xFF2B4570), Color(0xFF2A5340), Color(0xFF6A4A28))

/** 与 TimetableScreen 的 CourseInkLight / CourseInkDark 一致。 */
private val PreviewInkLight = Color(0xFF1A1A1A)
private val PreviewInkDark = Color(0xFFF3F3F3)

/** 解析 "#RRGGBB" / "#AARRGGBB"（可不带 #），非法返回 null；规则同 TimetableScreen.parseHexColor。 */
private fun parsePreviewHex(hex: String): Color? {
    val raw = hex.trim().removePrefix("#").trim()
    if (raw.length != 6 && raw.length != 8) return null
    if (!raw.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) Color(0xFF000000L or value) else Color(value)
}

/** 一档可选项：左侧文字、右侧当前值 / 色块预览 / 勾选。 */
@Composable
private fun ChoiceRow(
    label: String,
    summary: String,
    swatch: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    value: String = "",
) {
    val outline = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
    ArrowPreference(
        title = label,
        summary = summary,
        endActions = {
            if (value.isNotBlank()) ValueText(value)
            if (swatch != null) {
                Box(
                    Modifier
                        .padding(end = Space.s)
                        .size(20.dp)
                        .clip(Radius.small)
                        .background(swatch)
                        // 中性描边：浅色块在浅底、白色块在深底上都能看清边界。
                        .border(width = 1.dp, color = outline, shape = Radius.small),
                )
            }
            if (selected) CheckMark()
        },
        onClick = onClick,
    )
}

/** 选择对话框：标题 + 可滚动的档位列表 + 底部取消按钮，版式与 ConfirmDialog 一致。 */
@Composable
private fun ChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            insideMargin = PaddingValues(vertical = 20.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    title,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Space.s))
                // 色块底色有 12 档，小屏横竖都可能放不下，内容区要能滚；取消按钮固定在底部。
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
                Spacer(Modifier.height(Space.m))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    minHeight = 44.dp,
                ) { Text("取消") }
            }
        }
    }
}

private data class UiScaleChoice(val value: Float, val summary: String)

private val UiScaleChoices = listOf(
    UiScaleChoice(0.85f, "更紧凑"),
    UiScaleChoice(0.92f, "略紧凑"),
    UiScaleChoice(1.0f, "默认大小"),
    UiScaleChoice(1.08f, "略放大"),
    UiScaleChoice(1.15f, "更大"),
    UiScaleChoice(1.25f, "最大"),
)

private fun uiScaleLabel(value: Float): String {
    val match = UiScaleChoices.firstOrNull { kotlin.math.abs(it.value - value) < 0.004f }
    return "${(match?.value ?: value)}x"
}

private data class TintChoice(val hex: String, val label: String, val swatch: Color?)

/** 与 TimetableScreen 的 CourseTintsLight 对齐，另加一个 accent 蓝。 */
private val CourseTintChoices = listOf(
    TintChoice("", "跟随课程自动", null),
    TintChoice("#DCE9FF", "天蓝", Color(0xFFDCE9FF)),
    TintChoice("#D8F3E4", "薄荷", Color(0xFFD8F3E4)),
    TintChoice("#FFE6CC", "暖橙", Color(0xFFFFE6CC)),
    TintChoice("#EADBFF", "浅紫", Color(0xFFEADBFF)),
    TintChoice("#FFD9D6", "浅红", Color(0xFFFFD9D6)),
    TintChoice("#D4F1F6", "青蓝", Color(0xFFD4F1F6)),
    TintChoice("#DDE1FF", "靛蓝", Color(0xFFDDE1FF)),
    TintChoice("#FFF1C7", "淡黄", Color(0xFFFFF1C7)),
    TintChoice("#FFDCEA", "粉桃", Color(0xFFFFDCEA)),
    TintChoice("#D8F0D4", "浅绿", Color(0xFFD8F0D4)),
    TintChoice("#3482FF", "强调蓝", Color(0xFF3482FF)),
)

private fun tintLabel(hex: String): String {
    val normalized = hex.trim().removePrefix("#").uppercase()
    if (normalized.isEmpty()) return "跟随课程自动"
    return CourseTintChoices
        .firstOrNull { it.hex.removePrefix("#").uppercase() == normalized }
        ?.label
        ?: "自定义 #$normalized"
}

/** 存的 hex 可能不带 "#"，比较前统一归一化，否则标签有名字但预览色块和 ✓ 都对不上。 */
private fun normalizeTintHex(hex: String): String = hex.trim().removePrefix("#").uppercase()

private fun tintSwatch(hex: String): Color? = CourseTintChoices
    .firstOrNull { it.hex.isNotEmpty() && normalizeTintHex(it.hex) == normalizeTintHex(hex) }
    ?.swatch

private data class AlphaChoice(val value: Float, val summary: String)

private val CourseAlphaChoices = listOf(
    AlphaChoice(0.30f, "很透"),
    AlphaChoice(0.40f, "偏透"),
    AlphaChoice(0.55f, "适中"),
    AlphaChoice(0.70f, "偏实"),
    AlphaChoice(0.85f, "较实"),
    AlphaChoice(1.00f, "完全不透明"),
)

private data class InkChoice(
    val mode: String,
    val label: String,
    val hex: String,
    val swatch: Color?,
    val summary: String,
)

private val CourseInkChoices = listOf(
    InkChoice("auto", "自动", "", null, "按底色自动取黑/白字"),
    InkChoice("light", "黑色", "", Color(0xFF1A1A1A), "固定黑色文字"),
    InkChoice("dark", "白色", "", Color(0xFFFFFFFF), "固定白色文字"),
    InkChoice("custom", "深灰", "#333333", Color(0xFF333333), "固定 #333333"),
)

private fun inkLabel(mode: String): String =
    CourseInkChoices.firstOrNull { it.mode == mode }?.label ?: "自动"

private fun inkSwatch(mode: String): Color? =
    CourseInkChoices.firstOrNull { it.mode == mode }?.swatch
