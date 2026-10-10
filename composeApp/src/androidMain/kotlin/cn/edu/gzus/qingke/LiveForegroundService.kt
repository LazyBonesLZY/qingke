package cn.edu.gzus.qingke

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import cn.edu.gzus.qingke.data.LIVE_CHANNEL
import cn.edu.gzus.qingke.data.LIVE_ID
import cn.edu.gzus.qingke.data.buildLiveNotification
import cn.edu.gzus.qingke.shared.R
import cn.edu.gzus.qingke.data.cancelLiveClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * 上课期间的常驻前台服务：让 Live 通知一直挂着，并在分钟边界/上下课边沿重推
 * 一次（换 when 锚点、跟上进度条与正文）。
 *
 * 胶囊上的数字不靠这个服务逐秒刷新——那是系统计时器（when + Chronometer）自己
 * 走的，进程被冻结也不影响；这个服务管的是「通知别消失」和「锚点别过期」。
 *
 * 为什么必须用前台服务：ColorOS 会把后台闹钟整体冻结——实测把 `adjustment`
 * 推迟近 3 天（`setExactAndAllowWhileIdle` + `USE_EXACT_ALARM` 也照冻），
 * 所以用户不开「完全允许后台运行」时闹钟链根本不会醒。前台服务是系统认可
 * 的长驻身份，外卖/导航类 App 同款，不受该冻结策略影响。
 *
 * 通知就是 Live 通知本身（复用同一个 LIVE_ID 与 buildLiveNotification），
 * 不会多出一条常驻条目。
 *
 * 统一用 `startForegroundService`，并在 `onStartCommand` 中同步读取本地快照、构建通知、
 * 调 `startForeground`，满足 Android 的前台服务启动协议。后台入口若被系统拒绝，
 * 仍保留普通通知与下一次闹钟作为降级通道；前台恢复时会重新拉起服务。
 */
class LiveForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generation = AtomicLong(0L)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务启动协议要求尽快提升；通知参数只读本地快照，不做网络请求。
        val liveNotification = currentNotification()
        val notification = liveNotification ?: recoveryNotification()
        if (runCatching { startForeground(LIVE_ID, notification) }.isFailure) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (liveNotification == null) {
            // 先用兜底通知完成前台服务契约，再移除它，避免 startForegroundService
            // 在快照短暂不可读或通知刚被撤销时触发超时异常。
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val runGeneration = generation.incrementAndGet()
        loopJob?.cancel()
        loopJob = scope.launch {
            refreshLoop(runGeneration)
        }
        return START_STICKY
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

    private fun recoveryNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(LIVE_CHANNEL, "上课进度", NotificationManager.IMPORTANCE_DEFAULT),
        )
        return NotificationCompat.Builder(this, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle("青课")
            .setContentText("正在恢复上课提醒")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setSilent(true)
            .build()
    }

    /**
     * 重推一次，睡到下一次该醒的时刻。
     *
     * 醒的时刻取「分钟边界」和「上下课边沿」的较小值：只对齐分钟的话，上课那
     * 一刻到下一次分钟边界之间，通知里的 when 还指着已经过去的上课点，系统
     * 计时器会显示成负数。
     */
    private suspend fun refreshLoop(runGeneration: Long) {
        try {
            val minuteMs = 60_000L
            while (scope.isActive && generation.get() == runGeneration) {
                val nowMs = System.currentTimeMillis()
                val params = currentLiveParams(nowMs) ?: return
                val edge = if (nowMs < params.startMillis) params.startMillis else params.endMillis
                val toMinute = minuteMs - (nowMs % minuteMs) + 1_000L
                val toEdge = edge - nowMs + 1_000L
                delay(minOf(toMinute, toEdge).coerceAtLeast(1_000L))
                if (generation.get() != runGeneration) return
                val notification = currentNotification() ?: return
                getSystemService(NotificationManager::class.java)?.notify(LIVE_ID, notification)
            }
        } finally {
            if (generation.get() == runGeneration) {
                cancelLiveClass()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        generation.incrementAndGet()
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /**
         * 有课就拉起常驻刷新，没课就撤掉。
         * 统一走 startForegroundService；后台若被系统拒绝，保留普通通知与闹钟降级，
         * 前台恢复时再次启动服务。
         */
        fun sync(context: Context, active: Boolean) {
            val intent = Intent(context, LiveForegroundService::class.java)
            runCatching {
                if (active) {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } else {
                    context.stopService(intent)
                }
            }
        }
    }
}
