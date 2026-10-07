package cn.edu.gzus.qingke

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import cn.edu.gzus.qingke.data.BACKGROUND_WALLPAPER
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
actual fun QingkeBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun ApplySystemBars() {
    val view = LocalView.current
    val surface = MiuixTheme.colorScheme.surface
    val dark = isSystemInDarkTheme()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.decorView.setBackgroundColor(surface.toArgb())
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
actual fun QingkeBackgroundPickerButton(
    label: String,
    onPicked: (String) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPicked(uri.toString())
        }
    }
    Button(onClick = { launcher.launch(arrayOf("image/*")) }) {
        Text(label)
    }
}

@Composable
actual fun rememberQingkeBackgroundImage(source: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(source) {
        runCatching {
            val bitmap = when (source) {
                BACKGROUND_WALLPAPER -> wallpaperBitmap(context)
                else -> {
                    val uri = Uri.parse(source)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
            } ?: return@runCatching null
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}

private fun wallpaperBitmap(context: android.content.Context): Bitmap? {
    val drawable = WallpaperManager.getInstance(context).drawable ?: return null
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val max = 2048f
    val scale = if (w > max || h > max) (max / maxOf(w, h)) else 1f
    val dw = (w * scale).toInt().coerceAtLeast(1)
    val dh = (h * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, dw, dh)
    drawable.draw(canvas)
    return bitmap
}
