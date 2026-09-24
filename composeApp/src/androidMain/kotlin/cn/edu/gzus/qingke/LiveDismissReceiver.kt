package cn.edu.gzus.qingke

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.edu.gzus.qingke.data.cancelLiveClass
import cn.edu.gzus.qingke.data.liveNotice
import cn.edu.gzus.qingke.data.nextLiveWake
import cn.edu.gzus.qingke.data.notifyLiveClass
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.readSnapshotStore
import cn.edu.gzus.qingke.data.scheduleLiveWake

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
    val snap = readSnapshotStore()
    if (snap == null) {
        cancelLiveClass()
        scheduleLiveWake(null)
        return
    }
    val now = nowDateTime()
    val nowMs = System.currentTimeMillis()
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
            chip = notice.chip,
        )
    }
    scheduleLiveWake(snap.nextLiveWake(now, nowMs))
}
