package cn.edu.gzus.qingke

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import cn.edu.gzus.qingke.data.refreshHomeWidgets
import cn.edu.gzus.qingke.data.liveMasterEnabled
import cn.edu.gzus.qingke.data.tomorrowReminderEnabled
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    companion object {
        private var active: WeakReference<MainActivity>? = null
        fun requestNotificationPermission(fromUser: Boolean) {
            active?.get()?.let { activity -> activity.runOnUiThread { activity.askNotificationPermission(fromUser) } }
        }
    }

    private var askedNotificationPermission = false
    private var notificationPermissionInFlight = false

    private val notifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationPermissionInFlight = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        active = WeakReference(this)
        askedNotificationPermission = savedInstanceState?.getBoolean("notification_permission_asked") == true
        if (liveMasterEnabled() || tomorrowReminderEnabled()) askNotificationPermission()
        setContent { App() }
    }

    private fun askNotificationPermission(fromUser: Boolean = false) {
        if (Build.VERSION.SDK_INT < 33 || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || isFinishing || isDestroyed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            askedNotificationPermission = true
            return
        }
        if (notificationPermissionInFlight || (askedNotificationPermission && !fromUser)) return
        // Startup and Compose effects may both request permission; launch once per Activity instance.
        askedNotificationPermission = true
        notificationPermissionInFlight = true
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_permission_asked", askedNotificationPermission)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (active?.get() === this) active = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (liveMasterEnabled() || tomorrowReminderEnabled()) askNotificationPermission()
        refreshHomeWidgets()
    }
}
