package cn.edu.gzus.qingke

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cn.edu.gzus.qingke.data.LIVE_ID
import cn.edu.gzus.qingke.data.buildLiveNotification
import cn.edu.gzus.qingke.data.cancelLiveClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 上课期间的常驻前台服务，负责按分钟刷新 Live 通知（胶囊上的「下课 23′」）。
 *
 * 为什么必须用前台服务：ColorOS 会把后台闹钟整体冻结——实测把 `adjustment`
 * 推迟近 3 天（`setExactAndAllowWhileIdle` + `USE_EXACT_ALARM` 也照冻），
 * 所以用户不开「完全允许后台运行」时闹钟链根本不会醒。前台服务是系统认可
 * 的长驻身份，外卖/导航类 App 同款，不受该冻结策略影响。
 *
 * 通知就是 Live 通知本身（复用同一个 LIVE_ID 与 buildLiveNotification），
 * 不会多出一条常驻条目。
 */
class LiveForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var looping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val params = currentLiveParams(System.currentTimeMillis())
        val notification = params?.let {
            buildLiveNotification(
                title = it.title,
                detail = it.detail,
                progress = it.progress,
                etaMinutes = it.etaMinutes,
                startMillis = it.startMillis,
                endMillis = it.endMillis,
                countdownEnabled = it.countdownEnabled,
            )
        }
        if (notification == null) {
            // 没有该挂的课：不用起服务，直接收摊。
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(LIVE_ID, notification)
        if (!looping) {
            looping = true
            scope.launch { refreshLoop() }
        }
        return START_STICKY
    }

    /** 对齐到下一分钟 +1s 重推一次，分钟文案翻转后 1 秒内跟上。 */
    private suspend fun refreshLoop() {
        val minuteMs = 60_000L
        while (scope.isActive) {
            delay(minuteMs - (System.currentTimeMillis() % minuteMs) + 1_000L)
            val params = currentLiveParams(System.currentTimeMillis())
            if (params == null) break
            val notification = buildLiveNotification(
                title = params.title,
                detail = params.detail,
                progress = params.progress,
                etaMinutes = params.etaMinutes,
                startMillis = params.startMillis,
                endMillis = params.endMillis,
                countdownEnabled = params.countdownEnabled,
            ) ?: break
            getSystemService(NotificationManager::class.java)?.notify(LIVE_ID, notification)
        }
        // 课上完了：撤掉通知并退出前台。
        cancelLiveClass()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** 有课就拉起常驻刷新，没课就撤掉。 */
        fun sync(context: Context, active: Boolean) {
            val intent = Intent(context, LiveForegroundService::class.java)
            runCatching {
                if (active) {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                    else context.startService(intent)
                } else {
                    context.stopService(intent)
                }
            }
        }
    }
}
