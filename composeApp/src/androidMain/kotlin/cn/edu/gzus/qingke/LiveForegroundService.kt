package cn.edu.gzus.qingke

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.edu.gzus.qingke.data.LIVE_ID
import cn.edu.gzus.qingke.data.LIVE_STEP_MILLIS
import cn.edu.gzus.qingke.data.buildLiveNotification
import cn.edu.gzus.qingke.data.liveTiming
import cn.edu.gzus.qingke.data.nextLiveWake
import cn.edu.gzus.qingke.data.dateTimeAt
import cn.edu.gzus.qingke.data.readSnapshotStore
import cn.edu.gzus.qingke.data.scheduleLiveWake
import cn.edu.gzus.qingke.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps the Live notification fresh during class; the Live card itself is a plain notification with a timeout. */
class LiveForegroundService : Service() {
    // Service callbacks and loop cleanup share Main: an old loop cannot remove a newer notification.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var generation = 0L
    private var loopJob: Job? = null
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "$packageName:live",
        )?.apply { setReferenceCounted(false) }
    }

    // 灭屏后 CPU 会睡，主线程 delay 按 uptime 计也跟着停。亮屏立刻重推一次，
    // 胶囊和卡片上的分钟数不会在用户看的时候慢一拍。
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            sync(context, true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenOn,
                IntentFilter(Intent.ACTION_SCREEN_ON),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "Register screen-on receiver failed", it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runGeneration = ++generation
        loopJob?.cancel()
        val liveNotification = runCatching { currentNotification() }
            .onFailure { Log.w(TAG, "Read Live notification failed", it) }
            .getOrNull()
        // 前台服务挂自己那条静默保活通知，上课通知单独发：系统不会按 setTimeoutAfter
        // 撤下前台服务的通知，进程一旦被厂商冻结（ColorOS 的 Hans 会冻结前台服务），
        // 过了上下课点通知就一直挂着、倒数走成负数。普通通知到点由系统自己撤，不依赖进程活着。
        if (runCatching { startForeground(KEEPALIVE_ID, keepaliveNotification()) }
                .onFailure { Log.w(TAG, "Start foreground failed", it) }.isFailure
        ) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        liveNotification?.let { getSystemService(NotificationManager::class.java)?.notify(LIVE_ID, it) }
        if (liveNotification == null) {
            // Complete the foreground-start contract even if the course ended during startup.
            finishLive(startId)
            return START_NOT_STICKY
        }
        loopJob = scope.launch { refreshLoop(runGeneration, startId) }
        return START_STICKY
    }

    private fun currentNotification(nowMs: Long = System.currentTimeMillis()): Notification? {
        val params = currentLiveParams(nowMs) ?: return null
        return buildLiveNotification(
            title = params.title,
            detail = params.detail,
            progress = params.progress,
            etaMinutes = params.etaMinutes,
            startMillis = params.startMillis,
            endMillis = params.endMillis,
            countdownEnabled = params.countdownEnabled,
            nowMillis = nowMs,
        )
    }

    /** 最低重要性：不进状态栏、折叠在静默区，只为满足前台服务必须挂一条通知。 */
    private fun keepaliveNotification(): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(KEEPALIVE_CHANNEL, "上课提醒后台刷新", NotificationManager.IMPORTANCE_MIN).apply {
                description = "上课期间刷新倒计时用，可以在这里关掉显示，不影响上课提醒"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        return NotificationCompat.Builder(this, KEEPALIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle("上课提醒正在刷新")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private suspend fun refreshLoop(runGeneration: Long, startId: Int) {
        var retry = false
        try {
            while (scope.isActive && generation == runGeneration) {
                val nowMs = System.currentTimeMillis()
                val params = currentLiveParams(nowMs) ?: return
                val timing = liveTiming(nowMs, params.startMillis, params.endMillis) ?: return
                // 系统闹钟只在上课/下课那一刻叫醒（灭屏睡着、服务被杀时的兜底）。
                // 平时的分钟文案由这个循环推，秒级倒数是系统计时器自己走，都不用一直占着 CPU；
                // 以前整节课持锁加每分钟一次闹钟，是上课期间最耗电的地方。
                scheduleLiveWake(timing.deadlineMillis)
                // 只在离上下课不到两分钟时拿 CPU：保证正好在边沿换阶段，倒数不会走成负数。
                // 判断的是「这次 delay 醒来时」离边沿多远，而不是现在：剩 3 分钟时那一跳
                // 不拿锁的话，灭屏 CPU 一睡 delay 就停住，根本走不到「剩 2 分钟」去拿锁。
                // 真错过了边沿，上课通知也会被系统按 setTimeoutAfter 撤掉，不会挂着负数；
                // 这里是尽量让连堂时下课点无缝换成下一节的「距上课」。
                if (timing.remainingMillis - timing.refreshDelayMillis < EDGE_HOLD_MILLIS) {
                    wakeLock?.acquire(timing.remainingMillis + 10_000L)
                } else {
                    releaseWakeLock()
                }
                delay(timing.refreshDelayMillis)
                if (generation != runGeneration) return
                // 醒来可能已过边沿（被冻结 / 迟到）：用同一时刻判课和建通知，连堂时直接换成下一节。
                val notification = currentNotification(System.currentTimeMillis()) ?: return
                getSystemService(NotificationManager::class.java)?.notify(LIVE_ID, notification)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            retry = true
            Log.w(TAG, "Live refresh failed", error)
        } finally {
            if (generation == runGeneration) finishLive(startId, retry)
        }
    }

    private fun finishLive(startId: Int, retry: Boolean = false) {
        val nowMs = System.currentTimeMillis()
        val params = runCatching { currentLiveParams(nowMs) }.getOrNull()
        val nextWake = if (retry) {
            nowMs + 60_000L
        } else if (params != null) {
            // A dismissed/blocked course stays silent until its end; do not spin every minute.
            params.endMillis + 1_000L
        } else {
            runCatching { readSnapshotStore()?.nextLiveWake(dateTimeAt(nowMs), nowMs) }.getOrNull()
        }
        try {
            // 上课通知不再随前台服务一起撤：没课可挂了就显式撤掉（被划掉的那条本来就没了）。
            if (params == null && !retry) getSystemService(NotificationManager::class.java)?.cancel(LIVE_ID)
            scheduleLiveWake(nextWake)
        } finally {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOn) }
        ++generation
        loopJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QingkeLive"
        private const val EDGE_HOLD_MILLIS = 2 * LIVE_STEP_MILLIS
        private const val KEEPALIVE_ID = LIVE_ID + 1
        private const val KEEPALIVE_CHANNEL = "qingke_live_keepalive"

        fun sync(context: Context, active: Boolean) {
            val intent = Intent(context, LiveForegroundService::class.java)
            runCatching {
                if (active) ContextCompat.startForegroundService(context, intent)
                else context.stopService(intent)
            }.onFailure { Log.w(TAG, "Sync foreground service failed", it) }
        }
    }
}
