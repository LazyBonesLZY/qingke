package cn.edu.gzus.qingke.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.edu.gzus.qingke.QingkeBackgroundPickerButton
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.BACKGROUND_CUSTOM
import cn.edu.gzus.qingke.data.BACKGROUND_SYSTEM
import cn.edu.gzus.qingke.data.BACKGROUND_WALLPAPER
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item { SmallTitle(text = "背景") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                BackgroundModeOption(
                    label = "跟随系统",
                    summary = "应用默认背景色",
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
                        onClear = { onUpdate { it.copy(backgroundImageUri = "") } },
                    )
                }
            }
        }
        item { SmallTitle(text = "效果") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                    summary = "当前 ${uiScaleLabel(settings.uiScale)} · 全局字号与间距同步缩放",
                    endActions = {
                        Text(
                            uiScaleLabel(settings.uiScale),
                            color = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = { showUiScaleDialog = true },
                )
            }
        }
        item { SmallTitle(text = "课表") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                    .padding(horizontal = 16.dp),
            ) {
                ChoiceRow(
                    label = "色块底色",
                    summary = tintLabel(settings.courseTintHex),
                    swatch = tintSwatch(settings.courseTintHex),
                    selected = false,
                    onClick = { showTintDialog = true },
                )
                ChoiceRow(
                    label = "色块透明度",
                    summary = "${(settings.courseTintAlpha * 100).toInt()}%",
                    swatch = null,
                    selected = false,
                    onClick = { showAlphaDialog = true },
                )
                ChoiceRow(
                    label = "文字颜色",
                    summary = inkLabel(settings.courseInkMode),
                    swatch = inkSwatch(settings.courseInkMode),
                    selected = false,
                    onClick = { showInkDialog = true },
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
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

@Composable
private fun BackgroundModeOption(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = label,
        summary = if (selected) "$summary · 当前" else summary,
        endActions = {
            if (selected) {
                Text(
                    "✓",
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun CustomBackgroundActions(
    uri: String,
    onPicked: (String) -> Unit,
    onClear: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        QingkeBackgroundPickerButton(
            label = if (uri.isBlank()) "选择图片" else "重新选择图片",
            onPicked = onPicked,
        )
    }
    if (uri.isNotBlank()) {
        ArrowPreference(
            title = "清除自定义图片",
            summary = uri.takeLast(48),
            onClick = onClear,
        )
    }
}

/** 一档可选项：左侧文字、右侧色块预览与勾选。 */
@Composable
private fun ChoiceRow(
    label: String,
    summary: String,
    swatch: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = label,
        summary = summary,
        endActions = {
            if (swatch != null) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(swatch)
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                        ),
                )
            }
            if (selected) {
                Text(
                    "✓",
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}

/** Miuix 风格的选择对话框：标题 + 若干档位 + 取消。 */
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
        ) {
            SmallTitle(text = title)
            content()
            ArrowPreference(
                title = "取消",
                summary = "不修改",
                endActions = {},
                onClick = onDismiss,
            )
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
