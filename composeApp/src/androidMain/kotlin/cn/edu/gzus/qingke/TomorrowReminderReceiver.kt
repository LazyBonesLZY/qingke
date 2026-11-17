package cn.edu.gzus.qingke

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.edu.gzus.qingke.data.*
import cn.edu.gzus.qingke.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun restoreTomorrowReminder() = rescheduleTomorrowReminder()

class TomorrowReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TOMORROW_ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                synchronized(tomorrowReminderLock) {
                    postReminder(context.applicationContext, intent.getStringExtra(TOMORROW_DATE))
                }
            } catch (error: Exception) {
                Log.w("QingkeTomorrow", "Post evening reminder failed", error)
            } finally {
                try {
                    scheduleTomorrowReminder(runCatching { tomorrowReminderEnabled() }.getOrDefault(false))
                } finally { pending.finish() }
            }
        }
    }

    private fun postReminder(context: Context, requestedDate: String?) {
        if (!tomorrowReminderEnabled()) return
        val nowMs = System.currentTimeMillis()
        val snapshot = readSnapshotStore() ?: return
        val prefs = context.getSharedPreferences("tomorrow_reminder", Context.MODE_PRIVATE)
        val hint = tomorrowNotificationHint(snapshot, nowMs, requestedDate, prefs.getString("last_date", null)) ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = "tomorrow_courses"
        manager.createNotificationChannel(NotificationChannel(channel, "明日课程提醒", NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val open = PendingIntent.getActivity(context, TOMORROW_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle("青课 · ${hint.title}")
            .setContentText(hint.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(hint.detail))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val posted = postNotificationWhileEnabled(
            enabled = {
                tomorrowReminderEnabled() && readSnapshotStore()?.let {
                    tomorrowNotificationHint(it, System.currentTimeMillis(), requestedDate, prefs.getString("last_date", null)) != null
                } == true
            },
            post = { manager.notify(TOMORROW_ID, notification) },
            cancel = { manager.cancel(TOMORROW_ID) },
        )
        // A failed/disabled post must not consume today's receipt.
        if (posted && !prefs.edit().putString("last_date", requestedDate).commit()) {
            Log.w("QingkeTomorrow", "Evening reminder accepted but receipt could not be saved")
        }
    }
}
