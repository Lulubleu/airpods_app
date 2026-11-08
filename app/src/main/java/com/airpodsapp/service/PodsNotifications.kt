package com.airpodsapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.airpodsapp.MainActivity
import com.airpodsapp.R
import com.airpodsapp.bluetooth.PodsState
import com.airpodsapp.protocol.PodComponent
import com.airpodsapp.protocol.PodsStatus
import com.airpodsapp.ui.popup.ConnectionPopupActivity

/** The ongoing notification shown while the AirPods are connected. */
object PodsNotifications {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "pods_status"

    private const val CONNECTION_NOTIFICATION_ID = 2
    private const val CONNECTION_CHANNEL_ID = "pods_connection"
    private const val CONNECTION_TIMEOUT_MS = 8_000L

    fun createChannels(context: Context) {
        val status = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            // Low: visible in the shade, no sound or heads-up on every battery change.
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        val connection = NotificationChannel(
            CONNECTION_CHANNEL_ID,
            context.getString(R.string.notification_connection_channel),
            // High: shown as a heads-up banner, but silent.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(status, connection))
    }

    /**
     * Short silent heads-up shown once per connection with the battery levels.
     * Tapping it opens the battery bottom sheet; it disappears by itself after a few seconds.
     */
    fun showConnected(context: Context, state: PodsState) {
        val status = state.status ?: return
        val openPopup = PendingIntent.getActivity(
            context,
            1,
            Intent(context, ConnectionPopupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val name = state.deviceName ?: context.getString(R.string.app_name)
        val notification = Notification.Builder(context, CONNECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pods)
            .setContentTitle(context.getString(R.string.notification_connected, name))
            .setContentText(summary(context, status))
            .setContentIntent(openPopup)
            .setAutoCancel(true)
            .setTimeoutAfter(CONNECTION_TIMEOUT_MS)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(CONNECTION_NOTIFICATION_ID, notification)
    }

    fun build(context: Context, state: PodsState): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = state.deviceName ?: context.getString(R.string.app_name)
        val text = state.status?.let { summary(context, it) } ?: context.getString(R.string.notification_waiting)

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pods)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun update(context: Context, state: PodsState) {
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(context, state))
    }

    /** e.g. "G 85 % · D 75 % · Boîtier 45 %". Absent components are omitted. */
    private fun summary(context: Context, status: PodsStatus): String {
        fun part(labelRes: Int, pod: PodComponent): String? {
            val percent = pod.batteryPercent ?: return null
            val charging = if (pod.isCharging) " ⚡" else ""
            return context.getString(labelRes, percent) + charging
        }

        val parts = when (status) {
            is PodsStatus.Stereo -> listOfNotNull(
                part(R.string.battery_left, status.left),
                part(R.string.battery_right, status.right),
                part(R.string.battery_case, status.case),
            )
            is PodsStatus.Single -> listOfNotNull(part(R.string.battery_single, status.battery))
        }
        return parts.joinToString(" · ").ifEmpty { context.getString(R.string.notification_waiting) }
    }
}
