package cn.edu.gzus.qingke

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.edu.gzus.qingke.data.LIVE_CHANNEL
import cn.edu.gzus.qingke.data.LIVE_ID
import cn.edu.gzus.qingke.data.buildLiveNotification
import cn.edu.gzus.qingke.data.liveTiming
import cn.edu.gzus.qingke.data.nextLiveWake
import cn.edu.gzus.qingke.data.nowDateTime
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

/** One native Live notification doubles as the foreground-service notification on every vendor. */
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runGeneration = ++generation
        loopJob?.cancel()
        val liveNotification = runCatching { currentNotification() }
            .onFailure { Log.w(TAG, "Read Live notification failed", it) }
            .getOrNull()
        val notification = liveNotification ?: recoveryNotification()
        if (runCatching { startForeground(LIVE_ID, notification) }
                .onFailure { Log.w(TAG, "Start foreground failed", it) }.isFailure
        ) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (liveNotification == null) {
            // Complete the foreground-start contract even if the course ended during startup.
            finishLive(startId)
            return START_NOT_STICKY
        }
        loopJob = scope.launch { refreshLoop(runGeneration, startId) }
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
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(LIVE_CHANNEL, "上课进度", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
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

    private suspend fun refreshLoop(runGeneration: Long, startId: Int) {
        var retry = false
        try {
            while (scope.isActive && generation == runGeneration) {
                val nowMs = System.currentTimeMillis()
                val params = currentLiveParams(nowMs) ?: return
                val timing = liveTiming(nowMs, params.startMillis, params.endMillis) ?: return
                scheduleLiveWake(nowMs + timing.refreshDelayMillis)
                // Bounded CPU lease: FGS alone does not keep coroutine timers awake on screen-off.
                wakeLock?.acquire(minOf(params.endMillis - nowMs + 10_000L, 120_000L))
                delay(timing.refreshDelayMillis)
                if (generation != runGeneration) return
                val notification = currentNotification() ?: return
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
            runCatching { readSnapshotStore()?.nextLiveWake(nowDateTime(), nowMs) }.getOrNull()
        }
        try {
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
        ++generation
        loopJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QingkeLive"

        fun sync(context: Context, active: Boolean) {
            val intent = Intent(context, LiveForegroundService::class.java)
            runCatching {
                if (active) ContextCompat.startForegroundService(context, intent)
                else context.stopService(intent)
            }.onFailure { Log.w(TAG, "Sync foreground service failed", it) }
        }
    }
}
