package cn.edu.gzus.qingke

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
 *
 * 启动方式用 `startService` 而不是 `startForegroundService`：后者要求服务在
 * 5 秒内必须调 `startForeground`，否则抛 `ForegroundServiceDidNotStartInTimeException`
 * 崩进程；而「该不该挂通知」要读盘才知道，冷启动时完全可能判出 null（用户
 * 划掉了通知、权限被撤），就会踩这个契约。`startService` 没有这个约束——
 * 有通知时服务自己 `startForeground` 提升即可。代价是只能在 App 处于前台时
 * 拉起（后台 startService 会被系统拒，runCatching 吞掉，通知照旧由闹钟链维护）。
 */
class LiveForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var looping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 判有没有课要读盘 + 反序列化整份快照，别放主线程。
        if (!looping) {
            looping = true
            scope.launch {
                try {
                    runService()
                } finally {
                    // 循环退出后必须复位，否则下一次 onStartCommand 会以为
                    // 循环还在跑而不再启动，通知挂着却不再刷新。
                    looping = false
                }
            }
        }
        return START_STICKY
    }

    private suspend fun runService() {
        val notification = currentNotification()
        if (notification == null) {
            // 没有该挂的课（划掉了通知 / 没权限 / 课已结束）：不用提升前台。
            stopSelf()
            return
        }
        // 提升失败（极少数厂商限制）就安静退场，别崩；通知本身已经发出去了。
        if (runCatching { startForeground(LIVE_ID, notification) }.isFailure) {
            stopSelf()
            return
        }
        refreshLoop()
    }

    private fun currentNotification(): Notification? {
        val params = currentLiveParams(System.currentTimeMillis()) ?: return null
        return buildLiveNotification(
            title = params.title,
            detail = params.detail,
            progress = params.progress,
            etaMinutes = params.etaMinutes,
            startMillis = params.startMillis,
            endMillis = params.endMillis,
            countdownEnabled = params.countdownEnabled,
        )
    }

    /**
     * 重推一次，睡到下一次该醒的时刻。
     *
     * 醒的时刻取「分钟边界」和「上下课边沿」的较小值：只对齐分钟的话，上课那
     * 一刻到下一次分钟边界之间，通知里的 when 还指着已经过去的上课点，系统
     * 计时器会显示成负数。
     */
    private suspend fun refreshLoop() {
        val minuteMs = 60_000L
        while (scope.isActive) {
            val nowMs = System.currentTimeMillis()
            val params = currentLiveParams(nowMs) ?: break
            val edge = if (nowMs < params.startMillis) params.startMillis else params.endMillis
            val toMinute = minuteMs - (nowMs % minuteMs) + 1_000L
            val toEdge = edge - nowMs + 1_000L
            delay(minOf(toMinute, toEdge).coerceAtLeast(1_000L))
            val notification = currentNotification() ?: break
            getSystemService(NotificationManager::class.java)?.notify(LIVE_ID, notification)
        }
        // 课上完了（或被划掉/没权限了）：撤通知并退出。
        cancelLiveClass()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /**
         * 有课就拉起常驻刷新，没课就撤掉。
         * 用 startService：App 在前台时才允许，后台会被系统拒（吞掉即可，
         * 那条通知仍由闹钟链维护）。
         */
        fun sync(context: Context, active: Boolean) {
            val intent = Intent(context, LiveForegroundService::class.java)
            runCatching {
                if (active) context.startService(intent) else context.stopService(intent)
            }
        }
    }
}
