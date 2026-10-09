package cn.edu.gzus.qingke

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.edu.gzus.qingke.data.cancelLiveClass
import cn.edu.gzus.qingke.data.LIVE_STEP_MILLIS
import cn.edu.gzus.qingke.data.liveNotice
import cn.edu.gzus.qingke.data.nextLiveWake
import cn.edu.gzus.qingke.data.notifyLiveClass
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.readLiveTestWindow
import cn.edu.gzus.qingke.data.readSnapshotStore
import cn.edu.gzus.qingke.data.scheduleLiveWake
import cn.edu.gzus.qingke.data.writeLiveTestWindow

internal const val LIVE_PREFS = "qingke_live"
internal const val LIVE_DISMISSED_KEY = "dismissed"
internal const val EXTRA_LIVE_DISMISS = "dismiss_key"
internal const val ACTION_LIVE_TICK = "cn.edu.gzus.qingke.LIVE_TICK"

class LiveDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_LIVE_DISMISS) ?: return
        context.getSharedPreferences(LIVE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LIVE_DISMISSED_KEY, key)
            .apply()
    }
}

/** 应用被划掉后，到点由系统叫醒这里更新或收起上课通知，再排下一次。 */
class LiveTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { runLiveTick() }
    }
}

/** 当前该挂的通知参数；null = 不该挂。 */
internal data class LiveParams(
    val title: String,
    val detail: String,
    val progress: Float,
    val etaMinutes: Int,
    val startMillis: Long,
    val endMillis: Long,
    val countdownEnabled: Boolean,
)

/**
 * 现在该挂什么通知。测试窗口优先（它和真实课次走同一条通知路，只是窗口落盘
 * 在本地），其次真实课表，都没有就 null。前台服务与后台 tick 共用这一份判断，
 * 保证两条路算出的内容完全一致。
 */
internal fun currentLiveParams(nowMs: Long): LiveParams? {
    val test = readLiveTestWindow()
    if (test != null && test.second > nowMs) {
        val (start, end) = test
        return LiveParams(
            title = "正在上课（测试）",
            detail = "3-4节 · 测试教室",
            progress = ((nowMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f),
            etaMinutes = ((end - nowMs) / 60_000L).toInt().coerceAtLeast(0),
            startMillis = start,
            endMillis = end,
            countdownEnabled = readSnapshotStore()?.settings?.liveCountdownEnabled == true,
        )
    }
    if (test != null) {
        // 后台跑完的测试：清掉落盘窗口，别留永久陈旧文件。
        writeLiveTestWindow(0L, 0L)
    }
    val snap = readSnapshotStore() ?: return null
    val notice = snap.liveNotice(nowDateTime(), nowMs) ?: return null
    return LiveParams(
        title = notice.title,
        detail = notice.detail,
        progress = notice.progress,
        etaMinutes = notice.etaMinutes,
        startMillis = notice.startMillis,
        endMillis = notice.endMillis,
        countdownEnabled = snap.settings.liveCountdownEnabled,
    )
}

internal fun runLiveTick() {
    val nowMs = System.currentTimeMillis()
    val params = currentLiveParams(nowMs)
    if (params != null) {
        notifyLiveClass(
            title = params.title,
            detail = params.detail,
            progress = params.progress,
            etaMinutes = params.etaMinutes,
            startMillis = params.startMillis,
            endMillis = params.endMillis,
            countdownEnabled = params.countdownEnabled,
        )
        scheduleLiveWake(minOf(nowMs + LIVE_STEP_MILLIS, params.endMillis + 1_000L))
        return
    }
    cancelLiveClass()
    scheduleLiveWake(readSnapshotStore()?.nextLiveWake(nowDateTime(), nowMs))
}
