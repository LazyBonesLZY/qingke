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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

private const val LIVE_CHANNEL = "qingke_live_class"
internal const val LIVE_ID = 1001

actual fun setLiveForeground(active: Boolean) {
    if (!QingkeApp.ready()) return
    cn.edu.gzus.qingke.LiveForegroundService.sync(QingkeApp.app, active)
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
    // 上课期间由前台服务按分钟刷新 Live 通知，不依赖被 ColorOS 冻结的后台闹钟，
    // 所以白名单/精确闹钟现在是加分项而不是必需项。
    return if (isBatteryWhitelisted(ctx)) {
        "后台保活正常，提醒和倒计时准时"
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

/**
 * 把「上课/下课」画成一张图标位图，放进胶囊左侧的图标槽。
 *
 * 收起态胶囊只有一个内容槽，文字和系统计时器互斥（挂了 setShortCriticalText
 * 就没有计时器）。把状态标签挪到图标槽，内容槽就能留给 when + Chronometer
 * 由系统自己走字——锚点发布一次就够，锁屏、后台、进程被杀都照走，不依赖保活。
 *
 * 状态栏小图标是白色蒙版渲染，所以画白字透明底。图标槽是正方形，位图也必须
 * 是正方形：长方形会被横向压扁，字就变形。
 */
/**
 * 只有 ColorOS 的流体云需要「状态标签挪到图标位」这一招：它收起态芯片只有一个
 * 内容槽，文字和系统计时器互斥，挂了文字就没倒计时。原生 Android 和别家 OEM
 * 没有这个限制，图标位该留给应用图标，文字照旧走 shortCriticalText。
 */
private val colorOsChip: Boolean by lazy {
    val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme")
}

private val liveLabelIconCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.drawable.Icon>()

private fun liveLabelIcon(text: String): android.graphics.drawable.Icon =
    liveLabelIconCache.getOrPut(text) {
        val size = 96
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        // 声明真实密度，否则 96px 会被当成 96dp 再缩一次。
        bmp.density = QingkeApp.app.resources.displayMetrics.densityDpi
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = size * 0.48f
        }
        // 两个全宽汉字按 0.48 字号排下来几乎顶满画布，粗体字形外扩会啃边。
        // 先量实际宽度，超了就等比缩到留 4% 边距。
        val maxWidth = size * 0.92f
        val measured = paint.measureText(text)
        if (measured > maxWidth) paint.textSize *= maxWidth / measured
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        android.graphics.Canvas(bmp).drawText(text, size / 2f, baseline, paint)
        android.graphics.drawable.Icon.createWithBitmap(bmp)
    }

/**
 * 构建 Live 通知。抽成独立函数是为了让前台服务能用同一个对象
 * startForeground——前台服务的通知必须就是这条 Live 通知本身，
 * 否则会多出一条常驻条目。
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
): Notification? {
    val ctx = QingkeApp.app
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }
    val key = "$title:$startMillis"
    val prefs = ctx.getSharedPreferences(LIVE_PREFS, android.content.Context.MODE_PRIVATE)
    if (prefs.getString(LIVE_DISMISSED_KEY, "") == key) return null
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return null
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
    // inClass 为假时锚点指上课点。万一上课点已经过去（边沿重推晚了一拍，
    // 比如服务刚被杀过），改指下课点，别让系统计时器读成负数。
    val whenMillis = when {
        inClass -> endMillis
        startMillis > now -> startMillis
        endMillis > now -> endMillis
        else -> now + 1_000L
    }
    val status = if (inClass) "上课中" else "下一节"
    // 收起态芯片只有一个内容槽，文字和系统计时器互斥：开倒计时就不挂文字，
    // 内容槽整条让给 when + Chronometer 由系统自己走字——锚点发一次就够，
    // 锁屏、后台、进程被冻结都照走，不靠保活。ColorOS 额外把状态标签画进图标位
    // （它的芯片不显示标题，只剩一个计时器会看不懂）；其它系统的图标位留给应用图标。
    // 关掉开关才挂静态状态词，那时内容槽没有计时器。
    val chipText = if (inClass) "上课中" else "即将上课"
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
    val blue = 0xFF3482FF.toInt()
    val track = 0xFFB7D2FF.toInt()
    if (Build.VERSION.SDK_INT >= 36) {
        val done = pct.coerceIn(0, 100)
        // 完成态不留轨道尾巴、起步不画 1 格蓝条：两段加起来恒 100。
        val left = 100 - done
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(done)
            .setProgressSegments(
                listOf(
                    Notification.ProgressStyle.Segment(done).setColor(blue),
                    Notification.ProgressStyle.Segment(left).setColor(track),
                ),
            )
            .setProgressTrackerIcon(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_live_now))
            .setProgressStartIcon(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_live_start))
            .setProgressEndIcon(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_live_end))
        val builder = Notification.Builder(ctx, LIVE_CHANNEL)
            .setSmallIcon(
                if (countdownEnabled && colorOsChip) {
                    liveLabelIcon(if (inClass) "下课" else "上课")
                } else {
                    android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_live)
                },
            )
            .setContentTitle(title)
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
            // 计时器无条件开着：这是通知卡片自己的倒计时，跟胶囊那个开关无关。
            // 开关只管收起态那一个内容槽显示文字还是计时器。
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setSubText(status)
            // 到点让系统自己收掉。ongoing 通知不会自己消失，而我们的唤醒链只要
            // 漏一次（Doze 延迟、进程被冻结、闹钟没排上）它就永远挂在那儿。
            // setTimeoutAfter 由 NotificationManagerService 自己计时，不依赖我们。
            .setTimeoutAfter((endMillis - now).coerceAtLeast(1_000L))
        // 开倒计时就不挂文字：内容槽要空出来给系统计时器（挂了文字就没计时器）。
        // 关掉开关才挂静态状态词。
        if (!countdownEnabled) builder.setShortCriticalText(chipText)
        builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        return builder.build()
    }
    // 低版本没有流体云提升（自定义视图会失去资格），就当普通通知用：
    // 卡片里内嵌 Chronometer 自己走字，静态文案和进度条靠定期重推刷新。
    val etaText = when {
        inClass -> "还剩 ${etaMinutes.coerceAtLeast(0)} 分"
        etaMinutes > 0 -> "${etaMinutes} 分后上课"
        else -> "即将上课"
    }
    val remainingMs = (if (inClass) endMillis else startMillis) - now
    val card = RemoteViews(ctx.packageName, R.layout.qingke_live_notification).apply {
        setTextViewText(R.id.qingke_live_title, title)
        setTextViewText(R.id.qingke_live_badge, status)
        setTextViewText(R.id.qingke_live_meta, detail.replace("\n", " · "))
        setTextViewText(R.id.qingke_live_eta, etaText)
        setProgressBar(R.id.qingke_live_progress, 100, pct, false)
        if (remainingMs > 0) {
            setChronometerCountDown(R.id.qingke_live_countdown, true)
            setChronometer(
                R.id.qingke_live_countdown,
                android.os.SystemClock.elapsedRealtime() + remainingMs,
                null,
                true,
            )
            setViewVisibility(R.id.qingke_live_countdown, android.view.View.VISIBLE)
        } else {
            setViewVisibility(R.id.qingke_live_countdown, android.view.View.GONE)
        }
    }
    val builder = NotificationCompat.Builder(ctx, LIVE_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_live)
        .setContentTitle(title)
        .setContentText(detail)
        .setCustomContentView(card)
        .setCustomBigContentView(card)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setSubText(status)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(launch)
        .setDeleteIntent(dismiss)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setSilent(true)
        .setWhen(whenMillis)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setProgress(100, pct, false)
        .setColor(blue)
        .setTimeoutAfter((endMillis - now).coerceAtLeast(1_000L))
    return builder.build()
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
): Boolean {
    val notification = buildLiveNotification(
        title = title,
        detail = detail,
        progress = progress,
        etaMinutes = etaMinutes,
        startMillis = startMillis,
        endMillis = endMillis,
        countdownEnabled = countdownEnabled,
    ) ?: return false
    QingkeApp.app.getSystemService(NotificationManager::class.java)
        ?.notify(LIVE_ID, notification)
    return true
}

actual fun cancelLiveClass() {
    // 撤通知的同时把常驻服务也停掉：退出登录 / 会话过期 / 换登录通道这些路径
    // 只调 cancelLiveClass，不停服务的话下一分钟服务会把通知推回来。
    setLiveForeground(false)
    QingkeApp.app.getSystemService(NotificationManager::class.java)?.cancel(LIVE_ID)
}

// 有精确闹钟权限就精确推边沿；没给退回非精确（Doze 可能晚几分钟，倒计时照样走）。
// 电池白名单另算：进设置页「后台保活」里点。
actual fun scheduleLiveWake(atMillis: Long?) {
    if (!QingkeApp.ready()) return
    val ctx = QingkeApp.app
    val alarms = ctx.getSystemService(android.app.AlarmManager::class.java) ?: return
    val pending = PendingIntent.getBroadcast(
        ctx,
        LIVE_ID,
        Intent(ctx, LiveTickReceiver::class.java).setAction(ACTION_LIVE_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    if (atMillis == null) {
        alarms.cancel(pending)
        return
    }
    // 没通知权限排了也白排：tick 起来读完快照只能 no-op，还占一次叫醒。
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        alarms.cancel(pending)
        return
    }
    // 精确闹钟是唯一可靠通道：ColorOS 对非精确闹钟施加 adjustment 策略，
    // 实测能把投递推迟近 3 天。USE_EXACT_ALARM（manifest 声明、33+ 安装即
    // 授予）让 setExactAndAllowWhileIdle 不看 canScheduleExactAlarms()——
    // 后者查的是 SCHEDULE_EXACT_ALARM 那个 appop 位，部分 OEM 上谎报。
    // 先无条件试精确（有 USE_EXACT_ALARM 时必成），抛异常才降级非精确。
    val exactOk = runCatching {
        alarms.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, atMillis, pending)
    }.isSuccess
    if (!exactOk) {
        runCatching { alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, atMillis, pending) }
    }
}
