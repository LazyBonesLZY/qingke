package cn.edu.gzus.qingke.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import cn.edu.gzus.qingke.QingkeApp
import cn.edu.gzus.qingke.TomorrowReminderReceiver

internal const val TOMORROW_ACTION = "cn.edu.gzus.qingke.TOMORROW_REMINDER"
internal const val TOMORROW_DATE = "date"
internal const val TOMORROW_ID = 20401
internal val tomorrowReminderLock = Any()
@Volatile private var scheduledTomorrowAt = 0L
@Volatile private var scheduledTomorrowDate = ""

private fun cancelTomorrowAlarm(context: Context) {
    scheduledTomorrowAt = 0L
    scheduledTomorrowDate = ""
    val intent = Intent(context, TomorrowReminderReceiver::class.java).setAction(TOMORROW_ACTION)
    val existing = PendingIntent.getBroadcast(context, TOMORROW_ID, intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
    existing?.let {
        context.getSystemService(AlarmManager::class.java)?.cancel(it)
        it.cancel()
    }
}

internal fun rescheduleTomorrowReminder() {
    if (!QingkeApp.ready()) return
    synchronized(tomorrowReminderLock) {
        runCatching { cancelTomorrowAlarm(QingkeApp.app) }
            .onFailure { Log.w("QingkeTomorrow", "Cancel old evening alarm failed", it) }
        scheduleTomorrowReminder(tomorrowReminderEnabled())
    }
}

actual fun scheduleTomorrowReminder(enabled: Boolean) {
    if (!QingkeApp.ready()) return
    synchronized(tomorrowReminderLock) {
        val context = QingkeApp.app
        runCatching {
            if (!tomorrowReminderEnabled()) {
                context.getSystemService(NotificationManager::class.java)?.cancel(TOMORROW_ID)
                cancelTomorrowAlarm(context)
                return
            }
            if (!enabled) return // A stale disabled callback must not cancel a newly enabled alarm.
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, TomorrowReminderReceiver::class.java).setAction(TOMORROW_ACTION)
            val at = nextTomorrowReminderAt(System.currentTimeMillis())
            val targetDate = dateTimeAt(at).date.toString()
            val existing = PendingIntent.getBroadcast(context, TOMORROW_ID, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            if (existing != null && scheduledTomorrowAt == at && scheduledTomorrowDate == targetDate) return
            val pending = PendingIntent.getBroadcast(context, TOMORROW_ID,
                intent.putExtra(TOMORROW_DATE, targetDate),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                if (canScheduleExact(context)) {
                    try {
                        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    } catch (_: SecurityException) {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    }
                } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                scheduledTomorrowAt = at
                scheduledTomorrowDate = targetDate
            } catch (error: Exception) {
                scheduledTomorrowAt = 0L
                scheduledTomorrowDate = ""
                pending.cancel() // Permit a later state update to retry scheduling.
                throw error
            }
        }.onFailure { Log.w("QingkeTomorrow", "Schedule evening alarm failed", it) }
    }
}
