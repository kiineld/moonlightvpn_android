package vpn.moonlight.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * The persistent tunnel notification.
 *
 * Text is supplied by the caller rather than built here, so all user-facing
 * strings stay in the app module's localised resources.
 */
class VpnNotifications(private val context: Context) {

    fun ensureChannel(channelName: String, channelDescription: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            // Low: the tunnel notification is a persistent status, not an alert.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = channelDescription
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        title: String,
        text: String,
        smallIconRes: Int,
        contentIntent: PendingIntent?,
        disconnectLabel: String,
        disconnectIntent: PendingIntent?,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(smallIconRes)
        .setContentTitle(title)
        .setContentText(text)
        // Ongoing keeps it out of the swipe-to-dismiss set. Note that Android 14+
        // lets a user dismiss a foreground-service notification anyway; the
        // service keeps running, and the system's own VPN key icon stays up.
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(contentIntent)
        .setSilent(true)
        .apply {
            // Survives "clear all" on the versions that still honour it.
            foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            if (disconnectIntent != null) {
                addAction(0, disconnectLabel, disconnectIntent)
            }
        }
        .build()

    companion object {
        const val CHANNEL_ID = "moonlight_tunnel"
        const val NOTIFICATION_ID = 1001

        fun activityIntent(context: Context, target: Class<*>): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
