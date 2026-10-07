package cn.edu.gzus.qingke

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.BACKGROUND_SYSTEM
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 当前生效的界面缩放倍率，供需要按比例反向补偿尺寸的组件读取
 * （例如需要保持"物理尺寸不变"的图表/图片）。
 */
val LocalQingkeUiScale = staticCompositionLocalOf { 1f }

/**
 * 主题入口。
 * - [settings.backgroundMode] != system 且有 [backgroundImage]：根处画壁纸 + scrim，
 *   把 surface/background/surfaceContainer 调成半透明，让壁纸透出来。
 * - 其余场景走纯色系，行为与原来一致。
 * - [settings.uiScale] 在 0.7~1.4 之间整体缩放：density 与 fontScale 同步乘，
 *   所以 dp 尺寸和字号一起变，布局比例保持一致。
 */
@Composable
fun QingkeTheme(
    settings: AppSettings = AppSettings(),
    backgroundImage: ImageBitmap? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val customBg = settings.backgroundMode != BACKGROUND_SYSTEM && backgroundImage != null
    val uiScale = settings.uiScale.coerceIn(0.7f, 1.4f)
    val lightColors = remember(customBg) {
        // 把 surface/background 拉到 0.72/0.78，让壁纸/自定义图真正透出来；
        // container 稍高一点（0.80），保证卡片/输入框仍可读。
        val surfaceAlpha = if (customBg) 0.72f else 1f
        val backgroundAlpha = if (customBg) 0.78f else 1f
        val containerAlpha = if (customBg) 0.80f else 1f
        lightColorScheme(
            primary = Color(0xFF3482FF),
            onPrimary = Color(0xFFFFFFFF),
            surface = Color(0xFFF7F7F7).copy(alpha = surfaceAlpha),
            onSurface = Color(0xFF000000),
            surfaceContainer = Color(0xFFFFFFFF).copy(alpha = containerAlpha),
            onBackground = Color(0xFF000000),
            background = Color(0xFFF7F7F7).copy(alpha = backgroundAlpha),
            secondaryVariant = Color(0xFFF0F0F0).copy(alpha = containerAlpha),
            onSecondaryVariant = Color(0xFF303030),
            outline = Color(0xFFD9D9D9),
        )
    }
    val darkColors = remember(customBg) {
        // 深色下背景更暗，alpha 再压一点让壁纸仍有存在感。
        val surfaceAlpha = if (customBg) 0.70f else 1f
        val backgroundAlpha = if (customBg) 0.76f else 1f
        val containerAlpha = if (customBg) 0.78f else 1f
        darkColorScheme(
            primary = Color(0xFF5B9DFF),
            onPrimary = Color(0xFF001A40),
            surface = Color(0xFF121212).copy(alpha = surfaceAlpha),
            onSurface = Color(0xFFF2F2F2),
            surfaceContainer = Color(0xFF1E1E1E).copy(alpha = containerAlpha),
            onBackground = Color(0xFFF2F2F2),
            background = Color(0xFF121212).copy(alpha = backgroundAlpha),
            secondaryVariant = Color(0xFF2A2A2A).copy(alpha = containerAlpha),
            onSecondaryVariant = Color(0xFFE0E0E0),
            outline = Color(0xFF3A3A3A),
        )
    }
    val controller = remember(dark, customBg) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.System,
            lightColors = lightColors,
            darkColors = darkColors,
            keyColor = Color(0xFF3482FF),
            isDark = dark,
        )
    }
    MiuixTheme(controller = controller) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * uiScale, base.fontScale * uiScale),
            LocalQingkeUiScale provides uiScale,
        ) {
            ApplySystemBars()
            Box(Modifier.fillMaxSize()) {
                if (customBg) {
                    Image(
                        bitmap = backgroundImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    // 用轻量底衬而不是整层 scrim：只稍微压暗一点，
                    // 让上面 α0.72~0.80 的 surface 可读，同时壁纸仍透得出。
                    val scrim = if (dark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
                    Box(Modifier.fillMaxSize().background(scrim))
                }
                content()
            }
        }
    }
}
