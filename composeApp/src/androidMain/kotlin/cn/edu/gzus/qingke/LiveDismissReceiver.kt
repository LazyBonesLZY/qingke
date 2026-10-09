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

internal fun runLiveTick() {
    val nowMs = System.currentTimeMillis()
    // 测试窗口优先：它和真实课次走同一条通知路，只是窗口落盘在本地。
    // 之前这里不认识它，测试通知被划到后台就再没人重推，看着像「胶囊不能刷新」。
    val test = readLiveTestWindow()
    if (test != null && test.second > nowMs) {
        val (start, end) = test
        val progress = ((nowMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
        notifyLiveClass(
            title = "正在上课（测试）",
            detail = "3-4节 · 测试教室",
            progress = progress,
            etaMinutes = ((end - nowMs) / 60_000L).toInt().coerceAtLeast(0),
            startMillis = start,
            endMillis = end,
        )
        scheduleLiveWake(minOf(nowMs + LIVE_STEP_MILLIS, end + 1_000L))
        return
    }
    if (test != null) {
        // 后台跑完的测试：清掉落盘窗口，别留永久陈旧文件。
        writeLiveTestWindow(0L, 0L)
    }
    val snap = readSnapshotStore()
    if (snap == null) {
        cancelLiveClass()
        scheduleLiveWake(null)
        return
    }
    val now = nowDateTime()
    val notice = snap.liveNotice(now, nowMs)
    if (notice == null) {
        cancelLiveClass()
    } else {
        notifyLiveClass(
            title = notice.title,
            detail = notice.detail,
            progress = notice.progress,
            etaMinutes = notice.etaMinutes,
            startMillis = notice.startMillis,
            endMillis = notice.endMillis,
        )
    }
    scheduleLiveWake(snap.nextLiveWake(now, nowMs))
}
