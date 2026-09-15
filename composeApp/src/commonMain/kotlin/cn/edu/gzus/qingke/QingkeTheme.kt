package cn.edu.gzus.qingke

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun QingkeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val lightColors = remember {
        lightColorScheme(
            primary = Color(0xFF3482FF),
            onPrimary = Color(0xFFFFFFFF),
            surface = Color(0xFFF7F7F7),
            onSurface = Color(0xFF000000),
            surfaceContainer = Color(0xFFFFFFFF),
            onBackground = Color(0xFF000000),
            background = Color(0xFFF7F7F7),
            secondaryVariant = Color(0xFFF0F0F0),
            onSecondaryVariant = Color(0xFF303030),
            outline = Color(0xFFD9D9D9),
        )
    }
    val darkColors = remember {
        darkColorScheme(
            primary = Color(0xFF5B9DFF),
            onPrimary = Color(0xFF001A40),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFF2F2F2),
            surfaceContainer = Color(0xFF1E1E1E),
            onBackground = Color(0xFFF2F2F2),
            background = Color(0xFF121212),
            secondaryVariant = Color(0xFF2A2A2A),
            onSecondaryVariant = Color(0xFFE0E0E0),
            outline = Color(0xFF3A3A3A),
        )
    }
    val controller = remember(dark) {
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
        content()
    }
}
