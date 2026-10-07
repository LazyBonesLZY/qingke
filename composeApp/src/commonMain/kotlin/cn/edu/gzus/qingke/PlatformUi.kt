package cn.edu.gzus.qingke

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun QingkeBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun ApplySystemBars()

/** 跨平台选择一张背景图片；Android 持久化 URI，桌面端保存文件路径。 */
@Composable
expect fun QingkeBackgroundPickerButton(
    label: String,
    onPicked: (String) -> Unit,
)

/** 读取手机壁纸或已选图片。失败时返回 null，调用方回退到纯色背景。 */
@Composable
expect fun rememberQingkeBackgroundImage(source: String): ImageBitmap?
