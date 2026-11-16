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
import android.util.Log
import android.util.AtomicFile
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.edu.gzus.qingke.EXTRA_LIVE_DEADLINE
import cn.edu.gzus.qingke.EXTRA_LIVE_DISMISS
import cn.edu.gzus.qingke.JwxtWebActivity
import cn.edu.gzus.qingke.LIVE_DISMISSED_KEY
import cn.edu.gzus.qingke.LIVE_PREFS
import cn.edu.gzus.qingke.ACTION_LIVE_TICK
import cn.edu.gzus.qingke.LiveDismissReceiver
import cn.edu.gzus.qingke.LiveTickReceiver
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

internal const val LIVE_CHANNEL = "qingke_live_class"
internal const val LIVE_ID = 1001
private val storeLock = Any()

actual fun setLiveForeground(active: Boolean) {
    if (!QingkeApp.ready()) return
    cn.edu.gzus.qingke.LiveForegroundService.sync(QingkeApp.app, active && liveMasterEnabled())
}

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

actual fun readStore(name: String): String? = synchronized(storeLock) {
    runCatching {
        AtomicFile(File(QingkeApp.app.filesDir, name))
            .openRead()
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }.getOrNull()
}

actual fun writeStore(name: String, value: String) = synchronized(storeLock) {
    val atomic = AtomicFile(File(QingkeApp.app.filesDir, name))
    val output = atomic.startWrite()
    try {
        output.write(value.toByteArray(Charsets.UTF_8))
        atomic.finishWrite(output)
    } catch (error: Throwable) {
        atomic.failWrite(output)
        throw error
    }
}

actual fun clearCookieStore() {
    wipeCookies()
}

actual fun currentCookies(): List<Pair<String, String>> = cookiePairs()

actual fun openUrl(url: String) {
    val ctx = QingkeApp.app
    val parsed = Uri.parse(url)
    val allowedHosts = setOf(
        "jwxt.gzus.edu.cn", "ecarduser.gzus.edu.cn", "ehall.gzus.edu.cn",
        "cas.gzus.edu.cn", "sso.gzus.edu.cn",
    )
    val inApp = parsed.scheme.equals("https", ignoreCase = true) &&
        parsed.host?.lowercase() in allowedHosts
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

actual fun requestLiveUpdatePermission() {
    // Runtime prompt is issued from MainActivity on launch.
}

/**
 * 精确闹钟是否可用。USE_EXACT_ALARM（manifest 声明、33+ 安装即授予）时恒真；
 * 否则 12 以下直接能用，以上看用户给没给 SCHEDULE_EXACT_ALARM。
 * 注意 canScheduleExactAlarms() 只查 appop 位，OEM 上可能谎报，别当唯一依据。
 */
private fun canScheduleExact(ctx: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    if (Build.VERSION.SDK_INT >= 33 &&
        androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            "android.permission.USE_EXACT_ALARM",
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        return true
    }
    return ctx.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true
}

private fun isBatteryWhitelisted(ctx: android.content.Context): Boolean =
    ctx.getSystemService(android.os.PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(ctx.packageName) == true

actual fun liveKeepaliveStatus(): String {
    val ctx = QingkeApp.app
    // Foreground refresh is universal; battery exemptions are an additional fallback.
    return if (isBatteryWhitelisted(ctx)) {
        "已开启电池白名单；通知由前台服务刷新"
    } else {
        "上课期间由前台服务刷新倒计时；加电池白名单可以更稳"
    }
}

actual fun openKeepaliveSettings() {
    val ctx = QingkeApp.app
    // 先要白名单：系统一次弹框点完，不用翻设置。
    if (!isBatteryWhitelisted(ctx)) {
        val white = Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(white) }.isSuccess) return
    }
    // 再要精确闹钟（Android 12+ 才要授权）。
    if (!canScheduleExact(ctx)) {
        val exact = Intent(
            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            android.net.Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(exact) }
    }
}

actual fun openLiveUpdateSettings() {
    val ctx = QingkeApp.app
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (nm != null && nm.getNotificationChannel(LIVE_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
        val channelSettings = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, LIVE_CHANNEL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { ctx.startActivity(channelSettings) }.isSuccess) return
    }
    if (nm != null && !nm.areNotificationsEnabled()) {
        val appSettings = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { ctx.startActivity(appSettings) }.isSuccess) return
    }
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
    if (nm != null && (!nm.areNotificationsEnabled() ||
            nm.getNotificationChannel(LIVE_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
    ) {
        return "上课提醒通知已关闭，可到系统设置开启"
    }
    return (oppoLiveStatus(ctx) ?: nm?.let { LiveAndroidBackend.status(ctx, it) } ?: "通知服务暂不可用") +
        " · 提前 $leadMinutes 分钟"
}

actual fun refreshHomeWidgets() {
    if (!QingkeApp.ready()) return
    cn.edu.gzus.qingke.widget.QingkeWidgets.refreshAll(QingkeApp.app)
}

/**
 * 构建 Live 通知，应用循环、后台闹钟和前台服务共用。
 * 它不能当前台服务的通知：系统不按 setTimeoutAfter 撤前台服务的通知，
 * 进程被冻结时就会挂着负数倒计时。
 * 返回 null 表示这次不该显示（没权限 / 已被划掉）。
 */
internal fun buildLiveNotification(
    title: String,
    detail: String,
    progress: Float,
    etaMinutes: Int,
    startMillis: Long,
    endMillis: Long,
    countdownEnabled: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
    attempt: LiveDeliveryAttempt? = null,
): Notification? {
    if (!liveMasterEnabled()) return null
    val ctx = QingkeApp.app
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return null
    val key = "$title:$startMillis"
    val prefs = ctx.getSharedPreferences(LIVE_PREFS, Context.MODE_PRIVATE)
    if (prefs.getString(LIVE_DISMISSED_KEY, "") == key) return null
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return null
    nm.createNotificationChannel(NotificationChannel(LIVE_CHANNEL, "上课提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "课前提醒与上课进度，静默显示，不响铃不振动"
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
    })
    if (!nm.areNotificationsEnabled() ||
        nm.getNotificationChannel(LIVE_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE
    ) return null

    val timing = liveTiming(nowMillis, startMillis, endMillis) ?: return null
    val status = timing.statusText
    val chip = if (countdownEnabled) timing.countdownText else status
    val launch = PendingIntent.getActivity(ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val dismiss = PendingIntent.getBroadcast(ctx, LIVE_ID,
        Intent(ctx, LiveDismissReceiver::class.java)
            // extras 不参与 PendingIntent 身份：旧通知到期时必须仍携带旧阶段的 key/deadline。
            .setAction(liveDismissIdentity(key, timing.deadlineMillis))
            .putExtra(EXTRA_LIVE_DISMISS, key)
            .putExtra(EXTRA_LIVE_DEADLINE, timing.deadlineMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val night = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val accent = if (night) 0xFF5B9DFF.toInt() else 0xFF3482FF.toInt()
    val track = if (night) 0xFF2A3A55.toInt() else 0xFFD6E5FF.toInt()
    // A standard retry must not invoke vendor providers or attach private extras.
    val (mode, approvedBusiness) = if (attempt == LiveDeliveryAttempt.STANDARD) {
        LivePresentation.STANDARD to null
    } else LiveAndroidBackend.detect(ctx, nm)

    // Always have a complete standard notification. If a vendor or API enhancement
    // fails, the student still receives a time and room in the regular shade.
    fun standard(): Notification = NotificationCompat.Builder(ctx, LIVE_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_live)
        .setContentTitle(title)
        .setContentText(detail)
        .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
        .setSubText(status)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(launch)
        .setDeleteIntent(dismiss)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setSilent(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setWhen(timing.deadlineMillis)
        .setShowWhen(countdownEnabled)
        .setUsesChronometer(countdownEnabled)
        .setChronometerCountDown(countdownEnabled)
        .setProgress(100, timing.progressPercent, false)
        .setColor(accent)
        .setTimeoutAfter(timing.remainingMillis)
        .build()

    // AOSP 16 (API36) standard API is also the ColorOS16 native Fluid Cloud path.
    // Never request a promoted chip when the user/ROM has disabled that permission.
    val notification = if (Build.VERSION.SDK_INT >= 36 && attempt != LiveDeliveryAttempt.STANDARD) {
        run {
            val done = timing.progressPercent.coerceIn(0, 100)
            val segments = buildList {
                if (done > 0) add(Notification.ProgressStyle.Segment(done).setColor(accent))
                if (done < 100) add(Notification.ProgressStyle.Segment(100 - done).setColor(track))
            }
            val style = Notification.ProgressStyle().setStyledByProgress(true)
                .setProgress(done).setProgressSegments(segments)
                .setProgressTrackerIcon(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_live_now))
                .setProgressStartIcon(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_live_start))
                .setProgressEndIcon(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_live_end))
            val builder = Notification.Builder(ctx, LIVE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_live)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(style)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(launch)
                .setDeleteIntent(dismiss)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setColor(accent)
                .setColorized(false)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(timing.deadlineMillis)
                .setShowWhen(countdownEnabled)
                .setUsesChronometer(countdownEnabled)
                .setChronometerCountDown(countdownEnabled)
                .setSubText(status)
                // Compact chip uses the minute label; expanded content retains its chronometer.
                .setShortCriticalText(chip)
                .setTimeoutAfter(timing.remainingMillis)
            if (mode != LivePresentation.STANDARD &&
                runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
            ) {
                // Even on approved HyperOS, request the AOSP live chip as a fallback.
                builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            }
            builder.build()
        }
    } else standard()
    if (mode in setOf(LivePresentation.HYPEROS_ISLAND, LivePresentation.HYPEROS_FOCUS) && approvedBusiness != null &&
        attempt != LiveDeliveryAttempt.AOSP_NATIVE && attempt != LiveDeliveryAttempt.STANDARD) {
        // Let the shared engine rebuild the native candidate if the vendor payload fails.
        attachHyperOsFocusExtras(ctx, notification, title, detail, chip, progress, startMillis,
            endMillis, nowMillis, approvedBusiness, mode == LivePresentation.HYPEROS_ISLAND, countdownEnabled)
    }
    return notification
}

/** 发一条 Live 通知（前台服务路径也走 buildLiveNotification）。 */
actual fun notifyLiveClass(
    title: String,
    detail: String,
    progress: Float,
    etaMinutes: Int,
    startMillis: Long,
    endMillis: Long,
    countdownEnabled: Boolean,
    nowMillis: Long,
): Boolean {
    if (!liveMasterEnabled()) {
        writeLiveTestWindow(0L, 0L)
        cancelLiveClass()
        scheduleLiveWake(null)
        return false
    }
    val ctx = QingkeApp.app
    val manager = ctx.getSystemService(NotificationManager::class.java) ?: return false
    // SystemUI owns promotion, so successful notify() only proves the base
    // notification was accepted. A rejected vendor/native build uses the next
    // attempt with the SAME notification id, never a second concurrent alert.
    val presentation = runCatching { LiveAndroidBackend.detect(ctx, manager).first }
        .getOrDefault(LivePresentation.STANDARD)
    val deadline = liveTiming(nowMillis, startMillis, endMillis)?.deadlineMillis ?: endMillis
    val oppoTicket = beginOppoLive(ctx, title, startMillis, deadline)
    var deliveryFailed = false
    val posted = deliverLiveWithFallback(
        attempts = liveFallbackChain(presentation, Build.VERSION.SDK_INT),
        build = { attempt ->
            runCatching {
                buildLiveNotification(title, detail, progress, etaMinutes, startMillis, endMillis,
                    countdownEnabled, nowMillis, attempt)
            }.onFailure {
                deliveryFailed = true
                Log.w("QingkeLive", "Live build $attempt failed; trying fallback", it)
            }
                .getOrNull()
        },
        eligible = { attempt, candidate ->
            if (attempt != LiveDeliveryAttempt.AOSP_NATIVE || Build.VERSION.SDK_INT < 36) true
            else runCatching {
                manager.canPostPromotedNotifications() && candidate.hasPromotableCharacteristics()
            }.getOrDefault(false)
        },
        post = { attempt, candidate ->
            runCatching { manager.notify(LIVE_ID, candidate) }
                .onFailure {
                    deliveryFailed = true
                    Log.w("QingkeLive", "Live post $attempt rejected; trying fallback", it)
                }
                .isSuccess
        },
    )
    val prefs = ctx.getSharedPreferences(LIVE_PREFS, Context.MODE_PRIVATE)
    if (posted) {
        prefs.edit().remove("post_fail_key").remove("post_fail_count").remove("post_retry_at").apply()
    } else if (deliveryFailed) {
        val key = "$title:$startMillis"
        val failures = if (prefs.getString("post_fail_key", "") == key) prefs.getInt("post_fail_count", 0) else 0
        val count = (failures + 1).coerceAtMost(3)
        prefs.edit().putString("post_fail_key", key).putInt("post_fail_count", count)
            .putLong("post_retry_at", livePostRetryAt(nowMillis, endMillis, count)).apply()
    } else {
        // Permission/channel disabled or dismissed: never retry against the user's choice.
        prefs.edit().remove("post_retry_at").apply()
    }
    if (posted) publishOppoLive(ctx, oppoTicket, title, detail, startMillis, endMillis, countdownEnabled)
    else endOppoFluidCloud(ctx)
    return posted
}

internal actual fun livePostFailureWake(title: String, startMillis: Long, endMillis: Long): Long {
    val prefs = QingkeApp.app.getSharedPreferences(LIVE_PREFS, Context.MODE_PRIVATE)
    if (prefs.getString("post_fail_key", "") != "$title:$startMillis") return endMillis
    return prefs.getLong("post_retry_at", endMillis).coerceAtMost(endMillis)
}

actual fun cancelLiveClass() {
    // 撤通知的同时把常驻服务也停掉：退出登录 / 会话过期 / 换登录通道这些路径
    // 只调 cancelLiveClass，不停服务的话下一分钟服务会把通知推回来。
    endOppoFluidCloud(QingkeApp.app)
    setLiveForeground(false)
    QingkeApp.app.getSystemService(NotificationManager::class.java)?.cancel(LIVE_ID)
}

// Only one alarm per stage boundary, not once per minute and NEVER setAlarmClock().
// setAlarmClock marks the task as the user's next real alarm and can surface an
// alarm icon. Use exact-while-idle if authorized, then inexact as graceful fallback.
actual fun scheduleLiveWake(atMillis: Long?) {
    if (!QingkeApp.ready()) return
    val ctx = QingkeApp.app
    val alarms = ctx.getSystemService(android.app.AlarmManager::class.java) ?: return
    val pending = PendingIntent.getBroadcast(ctx, LIVE_ID,
        Intent(ctx, LiveTickReceiver::class.java).setAction(ACTION_LIVE_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    if (atMillis == null || !liveMasterEnabled() || (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
        alarms.cancel(pending)
        return
    }
    val target = maxOf(atMillis, System.currentTimeMillis() + 1_000L)
    val exact = runCatching {
        if (canScheduleExact(ctx)) {
            alarms.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, target, pending)
            true
        } else false
    }.getOrDefault(false)
    if (!exact) {
        runCatching { alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, target, pending) }
            .onFailure { Log.w("QingkeLive", "Schedule boundary fallback failed", it) }
    }

}
