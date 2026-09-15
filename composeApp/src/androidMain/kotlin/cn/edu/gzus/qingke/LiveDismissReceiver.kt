package cn.edu.gzus.qingke

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal const val LIVE_PREFS = "qingke_live"
internal const val LIVE_DISMISSED_KEY = "dismissed"
internal const val EXTRA_LIVE_DISMISS = "dismiss_key"

class LiveDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_LIVE_DISMISS) ?: return
        context.getSharedPreferences(LIVE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LIVE_DISMISSED_KEY, key)
            .apply()
    }
}
