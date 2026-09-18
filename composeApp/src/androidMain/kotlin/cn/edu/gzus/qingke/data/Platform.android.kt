package cn.edu.gzus.qingke.data

import android.Manifest
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.edu.gzus.qingke.EXTRA_LIVE_DISMISS
import cn.edu.gzus.qingke.JwxtWebActivity
import cn.edu.gzus.qingke.LIVE_DISMISSED_KEY
import cn.edu.gzus.qingke.LIVE_PREFS
import cn.edu.gzus.qingke.LiveDismissReceiver
import cn.edu.gzus.qingke.MainActivity
import cn.edu.gzus.qingke.QingkeApp
import cn.edu.gzus.qingke.shared.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyStore
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val LIVE_CHANNEL = "qingke_live_class"
private const val LIVE_ID = 1001

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpCookies) { storage = PersistCookieStorage.shared }
    install(HttpTimeout) {
        requestTimeoutMillis = 25_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 25_000
    }
    followRedirects = true
    engine {
        config {
            retryOnConnectionFailure(true)
            followRedirects(false)
            followSslRedirects(false)
        }
    }
}

actual fun createBareHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        requestTimeoutMillis = 40_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 40_000
    }
    followRedirects = true
    engine {
        config {
            retryOnConnectionFailure(true)
            followRedirects(false)
            followSslRedirects(false)
        }
    }
}

actual fun lyuapEncrypt(password: String, modulusHex: String, exponentHex: String): String =
    lyuapEncryptJvm(password, modulusHex, exponentHex)

actual fun md5Hex(text: String): String {
    val digest = java.security.MessageDigest.getInstance("MD5").digest(text.toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        ?: error("验证码图片坏了，点一下换一张")

internal actual fun decodePngGray(bytes: ByteArray): GrayPng? {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val width = bmp.width
    val height = bmp.height
    val pixels = IntArray(width * height)
    bmp.getPixels(pixels, 0, width, 0, 0, width, height)
    val gray = IntArray(width * height) { i ->
        val c = pixels[i]
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        (r * 30 + g * 59 + b * 11) / 100
    }
    return GrayPng(width, height, gray)
}

private const val RELOGIN_FILE = "qingke-relogin.bin"
private const val RELOGIN_ALIAS = "qingke_relogin"

private fun reloginKey(): SecretKey? = runCatching {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getEntry(RELOGIN_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
        KeyGenParameterSpec.Builder(
            RELOGIN_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build(),
    )
    generator.generateKey()
}.getOrNull()

internal actual fun saveReloginSecret(studentId: String, password: String) {
    val id = studentId.trim()
    if (id.isBlank() || password.isEmpty()) return
    val key = reloginKey() ?: return
    runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val packed = cipher.iv + cipher.doFinal("$id\n$password".toByteArray(Charsets.UTF_8))
        File(QingkeApp.app.filesDir, RELOGIN_FILE).writeBytes(packed)
    }
}

internal actual fun loadReloginSecret(): Pair<String, String>? {
    val file = File(QingkeApp.app.filesDir, RELOGIN_FILE)
    if (!file.exists()) return null
    val key = reloginKey() ?: return null
    val raw = runCatching { file.readBytes() }.getOrNull() ?: return null
    if (raw.size <= 12) return null
    return runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        val text = cipher.doFinal(raw.copyOfRange(12, raw.size)).toString(Charsets.UTF_8)
        val split = text.indexOf('\n')
        if (split <= 0) null else text.substring(0, split) to text.substring(split + 1)
    }.getOrNull()
}

internal actual fun clearReloginSecret() {
    File(QingkeApp.app.filesDir, RELOGIN_FILE).delete()
    runCatching {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(RELOGIN_ALIAS)) store.deleteEntry(RELOGIN_ALIAS)
    }
}

actual fun rsaEncrypt(password: String, modulusB64: String, exponentB64: String): String {
    val decoder = Base64.getDecoder()
    val modulus = BigInteger(1, decoder.decode(modulusB64))
    val exponent = BigInteger(1, decoder.decode(exponentB64))
    val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
}

actual fun readStore(name: String): String? {
    val file = File(QingkeApp.app.filesDir, name)
    return if (file.exists()) file.readText() else null
}

actual fun writeStore(name: String, value: String) {
    File(QingkeApp.app.filesDir, name).writeText(value)
}

actual fun clearCookieStore() {
    wipeCookies()
}

actual fun currentCookies(): List<Pair<String, String>> = cookiePairs()

actual fun openUrl(url: String) {
    val ctx = QingkeApp.app
    val inApp = listOf(
        "jwxt.gzus.edu.cn",
        "ecarduser.gzus.edu.cn",
        "ehall.gzus.edu.cn",
        "cas.gzus.edu.cn",
        "sso.gzus.edu.cn",
    ).any { url.contains(it) }
    val intent = if (inApp) {
        Intent(ctx, JwxtWebActivity::class.java)
            .putExtra(JwxtWebActivity.EXTRA_URL, url)
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
}

actual fun copyToClipboard(text: String) {
    val cm = QingkeApp.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("qingke", text))
}

actual fun openXiaoaiSchedule() {
    val ctx = QingkeApp.app
    val pkgs = listOf("com.xiaomi.aischedule", "com.miui.voiceassist")
    for (pkg in pkgs) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: continue
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
        return
    }
    openUrl(XIAOAI_SCHEDULE_HOME)
}

actual fun requestLiveUpdatePermission() {
    // Runtime prompt is issued from MainActivity on launch.
}

actual fun openLiveUpdateSettings() {
    val ctx = QingkeApp.app
    val promoted = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = runCatching { ctx.startActivity(promoted) }.isSuccess
    if (!launched) {
        val fallback = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(fallback) }
    }
}

actual fun resetLiveDismiss() {
    QingkeApp.app.getSharedPreferences(LIVE_PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .remove(LIVE_DISMISSED_KEY)
        .apply()
}

actual fun liveUpdateStatus(leadMinutes: Int): String {
    val ctx = QingkeApp.app
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return "需要通知权限"
    }
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 36 && nm != null && !nm.canPostPromotedNotifications()) {
        return "通知能发，状态栏 Live 还没开"
    }
    return "下一节课前 ${leadMinutes} 分钟出现，上课中倒数下课"
}

actual fun refreshHomeWidgets() {
    if (!QingkeApp.ready()) return
    cn.edu.gzus.qingke.widget.QingkeWidgets.refreshAll(QingkeApp.app)
}

actual fun notifyLiveClass(
    title: String,
    detail: String,
    progress: Float,
    etaMinutes: Int,
    startMillis: Long,
    endMillis: Long,
    chip: String,
): Boolean {
    val ctx = QingkeApp.app
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val key = "$title:$startMillis"
    val prefs = ctx.getSharedPreferences(LIVE_PREFS, android.content.Context.MODE_PRIVATE)
    if (prefs.getString(LIVE_DISMISSED_KEY, "") == key) return false
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
    val channel = NotificationChannel(LIVE_CHANNEL, "上课进度", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "下一节课和上课中的 Live Update"
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
    }
    nm.createNotificationChannel(channel)
    val now = System.currentTimeMillis()
    val inClass = now >= startMillis && now < endMillis
    val pct = when {
        endMillis <= startMillis -> (progress * 100).toInt().coerceIn(0, 100)
        now <= startMillis -> 0
        now >= endMillis -> 100
        else -> (((now - startMillis) * 100L) / (endMillis - startMillis)).toInt().coerceIn(0, 100)
    }
    val whenMillis = if (inClass) endMillis else startMillis
    val chipText = chip.ifBlank {
        when {
            inClass -> "下课${etaMinutes}分"
            etaMinutes > 0 -> "${etaMinutes}分"
            else -> "即将"
        }
    }
    val launch = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val dismiss = PendingIntent.getBroadcast(
        ctx,
        LIVE_ID,
        Intent(ctx, LiveDismissReceiver::class.java).putExtra(EXTRA_LIVE_DISMISS, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    if (Build.VERSION.SDK_INT >= 36) {
        val tracker = android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_live)
        val blue = 0xFF3482FF.toInt()
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(pct)
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(blue)),
            )
            .setProgressPoints(
                listOf(
                    Notification.ProgressStyle.Point(0).setColor(blue),
                    Notification.ProgressStyle.Point(100).setColor(blue),
                ),
            )
            .setProgressTrackerIcon(tracker)
            .setProgressStartIcon(tracker)
            .setProgressEndIcon(tracker)
        val builder = Notification.Builder(ctx, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle(if (inClass) "正在上课 · $title" else "下一节 · $title")
            .setContentText(detail)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launch)
            .setDeleteIntent(dismiss)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(blue)
            .setColorized(false)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setSubText(if (inClass) "上课中" else "即将上课")
        builder.setShortCriticalText(chipText)
        builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        nm.notify(LIVE_ID, builder.build())
        return true
    }
    val builder = NotificationCompat.Builder(ctx, LIVE_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_live)
        .setContentTitle(if (inClass) "正在上课 · $title" else "下一节 · $title")
        .setContentText(detail)
        .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
        .setSubText(if (inClass) "上课中" else "即将上课")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(launch)
        .setDeleteIntent(dismiss)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setProgress(100, pct, false)
        .setSilent(true)
        .setWhen(whenMillis)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setColor(0xFF3482FF.toInt())
        .setRequestPromotedOngoing(true)
    nm.notify(LIVE_ID, builder.build())
    return true
}

actual fun cancelLiveClass() {
    QingkeApp.app.getSystemService(NotificationManager::class.java)?.cancel(LIVE_ID)
}
