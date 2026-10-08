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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // source 变了要重读，壁纸类还得定时刷新（换壁纸/深潜/live wallpaper 都可能滞后）。
    // tick 每 20s 触发一次 recomposition，配合 remember(source, tick) 让 bitmap 失效重载。
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(source) {
        if (source != BACKGROUND_WALLPAPER) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(20_000)
            tick++
        }
    }
    return remember(source, tick) {
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
    val manager = WallpaperManager.getInstance(context)
    // 1) 常规 drawable（静态壁纸走这里最快）。
    // 2) live wallpaper 的 drawable 往往取不到或画出来是空的，退到 peekDrawable / fastDrawable。
    // 3) 再不行用 getBitmap() —— WallpaperService 会把当前帧缓存下来。
    val candidates = listOf(
        runCatching { manager.drawable }.getOrNull(),
        runCatching { manager.peekDrawable() }.getOrNull(),
        runCatching { manager.fastDrawable }.getOrNull(),
        runCatching { manager.builtInDrawable }.getOrNull(),
    )
    for (drawable in candidates) {
        if (drawable == null) continue
        val bitmap = runCatching { drawableToBitmap(drawable) }.getOrNull() ?: continue
        if (bitmapHasPixels(bitmap)) return bitmap
    }
    // getBitmap() 不在公开 SDK 里（@SystemApi），编译期解析不到，只能反射调；失败就当没有。
    return runCatching {
        WallpaperManager::class.java.getMethod("getBitmap").invoke(manager) as? Bitmap
    }.getOrNull()
}

private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap? {
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    if (w <= 1 && h <= 1) return null
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

private fun bitmapHasPixels(bitmap: Bitmap): Boolean {
    // 采一个点看是不是全透明——有些 live wallpaper 会画出一帧全透明的 drawable。
    val x = bitmap.width / 2
    val y = bitmap.height / 2
    return bitmap.getPixel(x, y) ushr 24 != 0
}

@Composable
actual fun QingkeOnResume(onResume: () -> Unit) {
    val current by rememberUpdatedState(onResume)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

