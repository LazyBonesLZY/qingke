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
    // source 变了要重读；壁纸还得盯着换没换（换壁纸/深潜/live wallpaper 都可能滞后）。
    // 静态壁纸只在 id 变了时重读，以前每 20s 在主线程把整张壁纸重画一遍。
    // 动态壁纸 id 不变但画面会变，拿不到 id 的机型也一样，这两种仍按 20s 重读。
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(source) {
        if (source != BACKGROUND_WALLPAPER) return@LaunchedEffect
        var lastId = wallpaperId(context)
        while (true) {
            kotlinx.coroutines.delay(20_000)
            val id = wallpaperId(context)
            if (id < 0 || id != lastId || isLiveWallpaper(context)) {
                lastId = id
                tick++
            }
        }
    }
    // 解码放到 IO 线程：相册原图、整屏壁纸在主线程解要卡好几帧。重读期间先留着旧图。
    val image by androidx.compose.runtime.produceState<ImageBitmap?>(null, source, tick) {
        value = if (source.isBlank()) {
            null
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadBackground(context, source) }
        }
    }
    return image
}

private fun loadBackground(context: android.content.Context, source: String): ImageBitmap? =
    runCatching {
        val bitmap = when (source) {
            BACKGROUND_WALLPAPER -> wallpaperBitmap(context)
            else -> decodeSampled(context, Uri.parse(source))
        } ?: return@runCatching null
        bitmap.asImageBitmap()
    }.getOrNull()

/**
 * 相册原图常有一两千万像素，原样解出来是几十 MB 的位图，老机器直接 OOM。
 * 按屏幕降采样（2 的幂），解出来仍然盖得满整屏，横竖屏都够。
 */
private fun decodeSampled(context: android.content.Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val metrics = context.resources.displayMetrics
    val needShort = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    val needLong = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    val haveShort = minOf(bounds.outWidth, bounds.outHeight)
    val haveLong = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (haveShort / (sample * 2) >= needShort && haveLong / (sample * 2) >= needLong) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun wallpaperId(context: android.content.Context): Int =
    runCatching {
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }.getOrDefault(-1)

private fun isLiveWallpaper(context: android.content.Context): Boolean =
    runCatching { WallpaperManager.getInstance(context).wallpaperInfo != null }.getOrDefault(false)

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

