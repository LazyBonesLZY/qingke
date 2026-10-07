package cn.edu.gzus.qingke.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        item { Spacer(Modifier.height(32.dp)) }
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
