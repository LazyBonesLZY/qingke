package cn.edu.gzus.qingke

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.data.BACKGROUND_SYSTEM
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 主题入口。
 * - [settings.backgroundMode] != system 且有 [backgroundImage]：根处画壁纸 + scrim，
 *   把 surface/background/surfaceContainer 调成半透明，让壁纸透出来。
 * - 其余场景走纯色系，行为与原来一致。
 */
@Composable
fun QingkeTheme(
    settings: AppSettings = AppSettings(),
    backgroundImage: ImageBitmap? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val customBg = settings.backgroundMode != BACKGROUND_SYSTEM && backgroundImage != null
    val lightColors = remember(customBg) {
        val surfaceAlpha = if (customBg) 0.86f else 1f
        val containerAlpha = if (customBg) 0.92f else 1f
        lightColorScheme(
            primary = Color(0xFF3482FF),
            onPrimary = Color(0xFFFFFFFF),
            surface = Color(0xFFF7F7F7).copy(alpha = surfaceAlpha),
            onSurface = Color(0xFF000000),
            surfaceContainer = Color(0xFFFFFFFF).copy(alpha = containerAlpha),
            onBackground = Color(0xFF000000),
            background = Color(0xFFF7F7F7).copy(alpha = surfaceAlpha),
            secondaryVariant = Color(0xFFF0F0F0).copy(alpha = containerAlpha),
            onSecondaryVariant = Color(0xFF303030),
            outline = Color(0xFFD9D9D9),
        )
    }
    val darkColors = remember(customBg) {
        val surfaceAlpha = if (customBg) 0.82f else 1f
        val containerAlpha = if (customBg) 0.88f else 1f
        darkColorScheme(
            primary = Color(0xFF5B9DFF),
            onPrimary = Color(0xFF001A40),
            surface = Color(0xFF121212).copy(alpha = surfaceAlpha),
            onSurface = Color(0xFFF2F2F2),
            surfaceContainer = Color(0xFF1E1E1E).copy(alpha = containerAlpha),
            onBackground = Color(0xFFF2F2F2),
            background = Color(0xFF121212).copy(alpha = surfaceAlpha),
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
        ApplySystemBars()
        Box(Modifier.fillMaxSize()) {
            if (customBg) {
                Image(
                    bitmap = backgroundImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // scrim 保证可读性
                val scrim = if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.38f)
                Box(Modifier.fillMaxSize().background(scrim))
            }
            content()
        }
    }
}
