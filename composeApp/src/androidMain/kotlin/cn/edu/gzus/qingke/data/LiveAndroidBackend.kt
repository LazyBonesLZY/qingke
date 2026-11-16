package cn.edu.gzus.qingke.data

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.graphics.drawable.Icon
import cn.edu.gzus.qingke.shared.R
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Only public Android APIs and Xiaomi's documented notification protocol are used.
 * OPPO ColorOS 16+ uses the Android 16 Live Updates API; no guessed Oplus extras.
 * Xiaomi requires official business/scenario and signature approval in addition to
 * the OS/user permission checks. With no configured approved business ID, we NEVER
 * attach vendor extras and always retain the standard Android notification.
 */
internal object LiveAndroidBackend {
    private const val META_APPROVED_BUSINESS = "cn.edu.gzus.qingke.HYPER_ISLAND_APPROVED_BUSINESS"
    private const val FOCUS_URI = "content://miui.statusbar.notification.public"
    private const val FOCUS_KEY = "notification_focus_protocol"
    private const val CACHE_MS = 120_000L
    @Volatile private var cachedFocus: Pair<Long, Boolean>? = null
    private val focusChecking = AtomicBoolean(false)
    private val focusExecutor by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "QingkeFocusCheck").apply { isDaemon = true }
        }
    }

    private fun rom(): LiveRom {
        val maker = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        return when {
            maker in setOf("xiaomi", "redmi", "poco") || brand in setOf("xiaomi", "redmi", "poco") -> LiveRom.HYPEROS
            maker in setOf("oppo", "oneplus", "realme") || brand in setOf("oppo", "oneplus", "realme") -> LiveRom.COLOROS
            else -> LiveRom.AOSP
        }
    }

    private fun approvedBusiness(ctx: Context): String? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 33) {
            ContextFlags.applicationMetadata(ctx)
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getApplicationInfo(ctx.packageName, android.content.pm.PackageManager.GET_META_DATA)
        }
        flags.metaData?.getString(META_APPROVED_BUSINESS)?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    // Official Xiaomi docs call this a time-consuming provider query. Never block
    // the UI/service main thread; until a fresh result is available, fail closed.
    private fun hasFocusPermission(ctx: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        cachedFocus?.let { (then, granted) ->
            if (now >= then && now - then < CACHE_MS) return granted
        }
        if (focusChecking.compareAndSet(false, true)) {
            runCatching {
                focusExecutor.execute {
                    try {
                        val granted = runCatching {
                            val extras = Bundle().apply { putString("package", ctx.packageName) }
                            ctx.contentResolver.call(Uri.parse(FOCUS_URI), "canShowFocus", null, extras)
                                ?.getBoolean("canShowFocus", false) == true
                        }.getOrDefault(false)
                        cachedFocus = SystemClock.elapsedRealtime() to granted
                    } finally {
                        focusChecking.set(false)
                    }
                }
            }.onFailure { focusChecking.set(false) }
        }
        return false
    }

    fun detect(ctx: Context, nm: NotificationManager): Pair<LivePresentation, String?> {
        val rom = rom()
        val business = if (rom == LiveRom.HYPEROS) approvedBusiness(ctx) else null
        val protocol = if (rom == LiveRom.HYPEROS) {
            runCatching { Settings.System.getInt(ctx.contentResolver, FOCUS_KEY, 0) }.getOrDefault(0)
        } else 0
        // Do not call Xiaomi's slow provider unless the approved business is present.
        val focus = protocol >= 3 && business != null && hasFocusPermission(ctx)
        val promoted = Build.VERSION.SDK_INT >= 36 &&
            runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
        val mode = chooseLivePresentation(
            LiveSignals(rom, Build.VERSION.SDK_INT, promoted, protocol, focus, business != null),
        )
        return mode to business
    }

    fun status(ctx: Context, nm: NotificationManager): String {
        val (mode, _) = detect(ctx, nm)
        return when (mode) {
            LivePresentation.HYPEROS_ISLAND -> "超级岛协议与用户授权已就绪；能否上岛仍由小米系统审核决定"
            LivePresentation.COLOROS_FLUID -> "当前系统可请求 ColorOS 流体云（Android Live Updates）"
            LivePresentation.AOSP_PROMOTED -> "当前系统允许请求 Android 原生实时更新"
            LivePresentation.STANDARD -> {
                val nativeOff = Build.VERSION.SDK_INT >= 36 &&
                    !runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
                val prefix = if (nativeOff) "原生 Live 还没开；" else ""
                prefix + when (rom()) {
                    LiveRom.HYPEROS -> "澎湃OS 普通通知可用；超级岛需正式业务和签名审批"
                    LiveRom.COLOROS -> "ColorOS 使用普通通知；流体云未开启或当前系统不支持"
                    LiveRom.AOSP -> "系统使用普通通知；原生实时更新未开启或当前系统不支持"
                }
            }
        }
    }

    /**
     * Xiaomi's published OS3 demo template; NOT enabled without an approved scene.
     * The approval/business name MUST match the actual verified developer grant.
     * Standard AOSP notification remains complete when the ROM rejects the extras.
     */
    fun attachIsland(
        ctx: Context,
        notification: Notification,
        approvedBusiness: String,
        title: String,
        status: String,
        countdown: String,
    ) {
        val text = JSONObject().put("frontTitle", status).put("title", countdown)
            .put("content", title).put("useHighLight", false)
        val pic = JSONObject().put("type", 1).put("pic", "miui.focus.pic_imageText")
        val big = JSONObject().put("imageTextInfoLeft", JSONObject()
            .put("type", 1).put("picInfo", pic)
            .put("miui.focus.paramtextInfo", text))
            .put("picInfo", pic)
        val small = JSONObject().put("picInfo", pic)
        val island = JSONObject().put("islandProperty", 1)
            .put("bigIslandArea", big).put("smallIslandArea", small)
        val v2 = JSONObject().put("protocol", 1)
            .put("business", approvedBusiness)
            .put("updatable", true)
            .put("enableFloat", false)
            .put("filterWhenNoPermission", false)
            .put("ticker", status)
            .put("aodTitle", status)
            .put("param_island", island)
            .put("baseInfo", JSONObject().put("title", title).put("content", countdown).put("type", 2))
        val pics = Bundle().apply {
            putParcelable("miui.focus.pic_imageText", Icon.createWithResource(ctx, R.drawable.ic_live_now))
        }
        notification.extras.putBundle("miui.focus.pics", pics)
        notification.extras.putString("miui.focus.param", JSONObject().put("param_v2", v2).toString())
    }
}

/** Encapsulate API 33 package-info overload to avoid a deprecation in newer SDKs. */
private object ContextFlags {
    fun applicationMetadata(ctx: Context): android.content.pm.ApplicationInfo =
        ctx.packageManager.getApplicationInfo(
            ctx.packageName,
            android.content.pm.PackageManager.ApplicationInfoFlags.of(
                android.content.pm.PackageManager.GET_META_DATA.toLong(),
            ),
        )
}
