package cn.edu.gzus.qingke.data

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentProviderClient
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import cn.edu.gzus.qingke.shared.R
import org.json.JSONArray
import android.Manifest
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

/**
 * OEM live-notification adapters.
 *
 * Xiaomi: documented local notification extension (miui.focus.param).
 * OPPO: T/TAF 283-2025 IntelligentIntent client-side shareIntent ContentProvider protocol.
 *
 * All OEM provider calls are serialized off the main thread. A normal Android notification is
 * retained as the fallback whenever the vendor route is unavailable or rejects the payload.
 */
private const val TAG = "QingkeLiveOEM"
private const val OEM_PREFS = "qingke_live_oem"
private const val HYPEROS_SEQUENCE = "hyperos_focus_sequence"
private const val OPPO_ACTIVE_INTENT = "oppo_active_intent"
private const val OPPO_SHARE_AUTHORITY_FALLBACK = "IntelligentIntent"
private const val MIUI_FOCUS_PARAM = "miui.focus.param"
private const val MIUI_FOCUS_PICS = "miui.focus.pics"
private const val MIUI_IMAGE_TEXT_ICON = "miui.focus.pic_imageText"

private val oemExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "qingke-live-oem").apply { isDaemon = true }
}
private val deliveryLock = Any()
private val lifecycle = LiveOemLifecycle()

/** Attach Xiaomi's documented private focus/island payload to the already-built Android notice. */
internal fun attachHyperOsFocusExtras(
    context: Context,
    notification: Notification,
    title: String,
    detail: String,
    statusText: String,
    progress: Float,
    startMillis: Long,
    endMillis: Long,
    nowMillis: Long,
    approvedBusiness: String,
    islandSupported: Boolean,
    countdownEnabled: Boolean,
) {
    require(approvedBusiness.isNotBlank())

    val prefs = context.getSharedPreferences(OEM_PREFS, Context.MODE_PRIVATE)
    val sequence = prefs.getLong(HYPEROS_SEQUENCE, 0L) + 1L
    prefs.edit().putLong(HYPEROS_SEQUENCE, sequence).apply()

    val inClass = nowMillis >= startMillis
    val remainingMillis = (if (inClass) endMillis else startMillis) - nowMillis
    if (remainingMillis <= 0L) return
    val remainingMinutes = ((remainingMillis - 1L) / 60_000L + 1L).coerceAtLeast(1L)
    val percent = (progress * 100f).toInt().coerceIn(0, 100)
    val shortStatus = if (inClass) "上课中" else "即将上课"
    val countdown = statusText.ifBlank {
        if (inClass) "距下课${remainingMinutes}分" else "距上课${remainingMinutes}分"
    }
    val timeoutMinutes = ((remainingMillis + 59_999L) / 60_000L).toInt().coerceIn(1, 180)
    val business = approvedBusiness

    val paramV2 = JSONObject()
        .put("protocol", 1)
        .put("business", business)
        .put("islandFirstFloat", false)
        .put("enableFloat", false)
        .put("updatable", true)
        .put("timeout", timeoutMinutes)
        .put("reopen", "reopen")
        // Xiaomi explicitly documents false as: do not filter the notice when focus permission
        // is unavailable. The ordinary notification remains the fallback.
        .put("filterWhenNoPermission", false)
        .put("orderId", "qingke-$startMillis")
        .put("sequence", sequence)
        .put("ticker", "$shortStatus · $title".take(32))
        .put("aodTitle", "$shortStatus · $title".take(24))
        .put("baseInfo", JSONObject()
            .put("title", title.take(32))
            .put("content", detail.take(80))
            .put("colorTitle", "#3482FF")
            .put("type", 2))
        .put("extraInfo", JSONObject().put("courseProgress", percent))
        .put("hintInfo", JSONObject()
            .put("type", 1)
            .put("title", countdown))

    if (islandSupported) {
        val iconInfo = JSONObject().put("type", 1).put("pic", MIUI_IMAGE_TEXT_ICON)
        val textInfo = JSONObject()
            .put("frontTitle", shortStatus)
            .put("title", if (countdownEnabled) remainingMinutes.toString() else shortStatus)
            .put("content", if (countdownEnabled) { if (inClass) "分钟后下课" else "分钟后上课" } else title.take(32))
            .put("useHighLight", true)
        // These component names and the nested paramtextInfo key follow Xiaomi's published
        // OS3 example. Keep the schema conservative; unknown template fields are not invented.
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandTimeout", ((remainingMillis + 999L) / 1_000L).toInt().coerceIn(1, 7_200))
            .put("bigIslandArea", JSONObject()
                .put("imageTextInfoLeft", JSONObject()
                    .put("type", 1)
                    .put("picInfo", JSONObject().put("type", 1).put("pic", MIUI_IMAGE_TEXT_ICON))
                    .put("miui.focus.paramtextInfo", textInfo))
                .put("picInfo", iconInfo))
            .put("smallIslandArea", JSONObject().put("picInfo", iconInfo))
        paramV2.put("param_island", island)
    }

    notification.extras.putString(MIUI_FOCUS_PARAM, JSONObject().put("param_v2", paramV2).toString())
    if (islandSupported) {
        notification.extras.putBundle(MIUI_FOCUS_PICS, Bundle().apply {
            putParcelable(MIUI_IMAGE_TEXT_ICON, Icon.createWithResource(context, R.drawable.ic_stat_live))
        })
    }
}

internal fun oppoLiveStatus(context: Context): String? {
    if (!isColorOsFamily()) return null
    return if (readOppoConfig(context) != null) {
        "已配置 ColorOS 本地流体云；授权与展示由系统决定，失败保留上课通知"
    } else "ColorOS 本地流体云未配置；使用系统上课通知"
}

private fun systemProperty(name: String): String = runCatching {
    val type = Class.forName("android.os.SystemProperties")
    val get = type.getDeclaredMethod("get", String::class.java)
    (get.invoke(null, name) as? String).orEmpty()
}.getOrDefault("")

internal fun systemPropertyBoolean(name: String, default: Boolean): Boolean = runCatching {
    val type = Class.forName("android.os.SystemProperties")
    val getBoolean = type.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType!!)
    getBoolean.invoke(null, name, default) as? Boolean ?: default
}.getOrDefault(default)

/** Invalidate older frames BEFORE the accepted Android fallback is posted. */
internal fun beginOppoLive(context: Context, title: String, startMillis: Long, deadlineMillis: Long): LiveOemLifecycle.Ticket? {
    if (!isColorOsFamily()) return null
    return synchronized(deliveryLock) { lifecycle.begin("$title:$startMillis", deadlineMillis) }
}

private fun canShowLive(context: Context, key: String): Boolean {
    if (!liveMasterEnabled()) return false
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
    if (context.getSharedPreferences(cn.edu.gzus.qingke.LIVE_PREFS, Context.MODE_PRIVATE)
            .getString(cn.edu.gzus.qingke.LIVE_DISMISSED_KEY, "") == key) return false
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    return manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(LIVE_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
}

/**
 * Optional private replacement AFTER a real Android notification has been accepted.
 * Queue acceptance never reports notification success, and this worker never republishes a fallback.
 */
internal fun publishOppoLive(
    context: Context,
    ticket: LiveOemLifecycle.Ticket?,
    title: String,
    detail: String,
    startMillis: Long,
    endMillis: Long,
    countdownEnabled: Boolean,
) {
    if (ticket == null) return
    val ctx = context.applicationContext
    val config = readOppoConfig(ctx)
    if (config == null) { endOppoFluidCloud(ctx); return }
    runCatching {
        oemExecutor.execute {
            runCatching {
                fun current(): Boolean = synchronized(deliveryLock) {
                    lifecycle.isCurrent(ticket, System.currentTimeMillis()) && canShowLive(ctx, ticket.key)
                }
                if (!current()) return@execute
                val prefs = ctx.getSharedPreferences(OEM_PREFS, Context.MODE_PRIVATE)
                val previous = prefs.getString(OPPO_ACTIVE_INTENT, null)?.let {
                    runCatching { JSONObject(it) }.getOrNull()
                }
                val entityId = UUID.nameUUIDFromBytes(ticket.key.toByteArray(StandardCharsets.UTF_8)).toString()
                val previousId = previous?.optJSONObject("intentEntity")?.optString("entityId")
                if (previous != null && previousId != entityId) {
                    if (!endOppoIntent(ctx, previous)) return@execute
                    prefs.edit().remove(OPPO_ACTIVE_INTENT).apply()
                }
                if (!hasOppoIntentPermission(ctx, config) || !current()) {
                    if (previous != null && previousId == entityId && endOppoIntent(ctx, previous)) {
                        prefs.edit().remove(OPPO_ACTIVE_INTENT).apply()
                    }
                    return@execute
                }
                // Re-sample on the worker: never submit a pre-class frame after class starts.
                val now = System.currentTimeMillis()
                if (!current()) return@execute
                val payload = JSONObject(buildOppoLiveIntent(config, ctx.packageName, entityId, entityId,
                    title, detail, startMillis, endMillis, now,
                    if (previousId == entityId) 1 else 0, countdownEnabled))
                if (!shareOppoIntent(ctx, payload)) {
                    if (previous != null && previousId == entityId && endOppoIntent(ctx, previous)) {
                        prefs.edit().remove(OPPO_ACTIVE_INTENT).apply()
                    }
                    return@execute
                }
                // Retain recovery information even if cancellation races the provider reply.
                prefs.edit().putString(OPPO_ACTIVE_INTENT, payload.toString()).apply()
                val accepted = synchronized(deliveryLock) {
                    if (lifecycle.isCurrent(ticket, System.currentTimeMillis()) && canShowLive(ctx, ticket.key)) {
                        ctx.getSystemService(NotificationManager::class.java)?.cancel(LIVE_ID)
                        true
                    } else false
                }
                if (!accepted && endOppoIntent(ctx, payload)) prefs.edit().remove(OPPO_ACTIVE_INTENT).apply()
            }.onFailure { Log.w(TAG, "ColorOS private update failed; accepted Android notice retained", it) }
        }
    }.onFailure { Log.w(TAG, "Queue ColorOS private update failed; Android notice retained", it) }
}

internal fun endOppoFluidCloud(context: Context) {
    if (!isColorOsFamily()) return
    synchronized(deliveryLock) { lifecycle.cancel() }
    val ctx = context.applicationContext
    runCatching {
        oemExecutor.execute {
            runCatching {
                val prefs = ctx.getSharedPreferences(OEM_PREFS, Context.MODE_PRIVATE)
                val raw = prefs.getString(OPPO_ACTIVE_INTENT, null) ?: return@execute
                val active = runCatching { JSONObject(raw) }.getOrNull() ?: run {
                    prefs.edit().remove(OPPO_ACTIVE_INTENT).apply()
                    return@execute
                }
                if (endOppoIntent(ctx, active)) prefs.edit().remove(OPPO_ACTIVE_INTENT).apply()
            }.onFailure { Log.w(TAG, "End ColorOS private update failed; recovery record retained", it) }
        }
    }.onFailure { Log.w(TAG, "Queue ColorOS end failed; recovery record retained", it) }
}

private fun readOppoConfig(context: Context): OppoFluidCloudConfig? = runCatching {
    val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    val metadata = info.metaData ?: return null
    val intentName = metadata.getString("cn.edu.gzus.qingke.OPPO_FLUID_CLOUD_INTENT_NAME").orEmpty().trim()
    val intentVersion = metadata.getString("cn.edu.gzus.qingke.OPPO_FLUID_CLOUD_INTENT_VERSION").orEmpty().trim()
    val serviceIds = metadata.getString("cn.edu.gzus.qingke.OPPO_FLUID_CLOUD_SERVICE_IDS")
        .orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotBlank() }
    fun readInt(name: String): Int = when (val value = metadata.get(name)) {
        is Int -> value
        is String -> value.toIntOrNull() ?: -1
        else -> -1
    }
    val upcoming = readInt("cn.edu.gzus.qingke.OPPO_MILESTONE_UPCOMING")
    val ongoing = readInt("cn.edu.gzus.qingke.OPPO_MILESTONE_ONGOING")
    val ended = readInt("cn.edu.gzus.qingke.OPPO_MILESTONE_ENDED")
    OppoFluidCloudConfig(intentName, intentVersion, serviceIds, upcoming, ongoing, ended).takeIf { it.complete }
}.onFailure { Log.w(TAG, "Read OPPO Fluid Cloud config failed", it) }.getOrNull()

private fun hasOppoIntentPermission(context: Context, config: OppoFluidCloudConfig): Boolean {
    if (runCatching {
            Settings.Global.getInt(context.contentResolver, "support_intelligent_intent", 0) <= 0
        }.getOrDefault(true)
    ) return false

    val authority = Settings.Global.getString(context.contentResolver, "intelligent_intent_authorities")
        ?.takeIf { it.isNotBlank() } ?: OPPO_SHARE_AUTHORITY_FALLBACK
    return withOppoProvider(context, authority) { client ->
        val featureArgs = JSONObject()
            .put("enableIntelligentIntent", JSONArray())
            .put("userAcceptShareIntent", JSONArray().put(config.intentName))
            .put("querySupportIntent", JSONArray().put(config.intentName))
        val result = client.call(
            "queryFeature",
            "enableIntelligentIntent,userAcceptShareIntent,querySupportIntent",
            Bundle().apply { putString("featureArgs", featureArgs.toString()) },
        ) ?: return@withOppoProvider false
        oppoFeaturesAllowShare(result.getString("result"), config)
    } ?: false
}

private fun shareOppoIntent(context: Context, payload: JSONObject): Boolean {
    val authority = Settings.Global.getString(context.contentResolver, "intelligent_intent_authorities")
        ?.takeIf { it.isNotBlank() } ?: OPPO_SHARE_AUTHORITY_FALLBACK
    return withOppoProvider(context, authority) { client ->
        val result = client.call("shareIntent", null, Bundle().apply {
            putString("intentData", payload.toString())
        }) ?: return@withOppoProvider false
        val response = result.getString("result")?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return@withOppoProvider false
        response.optInt("code", -1) == 0
    } ?: false
}

private fun endOppoIntent(context: Context, active: JSONObject): Boolean {
    val ending = runCatching {
        JSONObject(active.toString()).apply {
            put("timestamp", System.currentTimeMillis())
            optJSONObject("intentAction")?.put("actionStatus", 2)
            val endedCode = optJSONObject("extra")?.optInt("milestoneEndedCode", -1) ?: -1
            optJSONObject("intentEntity")?.optJSONObject("milestone")?.apply {
                put("text", "completed")
                if (endedCode >= 0) put("code", endedCode)
            }
        }
    }.getOrNull() ?: return false
    return runCatching { shareOppoIntent(context, ending) }
        .onFailure { Log.w(TAG, "End OPPO Fluid Cloud failed", it) }
        .getOrDefault(false)
}

private inline fun <T> withOppoProvider(
    context: Context,
    authority: String,
    block: (ContentProviderClient) -> T,
): T? {
    val client = runCatching { context.contentResolver.acquireUnstableContentProviderClient(authority) }
        .onFailure { Log.w(TAG, "Acquire IntelligentIntent provider failed", it) }
        .getOrNull() ?: return null
    return try {
        block(client)
    } catch (error: Exception) {
        Log.w(TAG, "IntelligentIntent provider call failed", error)
        null
    } finally {
        runCatching { client.close() }
    }
}

private fun isColorOsFamily(): Boolean {
    val brand = Build.BRAND.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    val knownBrand = listOf("oppo", "oneplus", "realme").any {
        brand.contains(it, ignoreCase = true) || manufacturer.contains(it, ignoreCase = true)
    }
    return knownBrand || systemProperty("ro.build.version.opporom").isNotBlank() ||
        systemProperty("ro.build.version.oplusrom").isNotBlank()
}
