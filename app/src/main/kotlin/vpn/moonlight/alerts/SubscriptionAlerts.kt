package vpn.moonlight.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import vpn.moonlight.MoonlightApplication
import vpn.moonlight.R

/**
 * Background check for a subscription that is about to run out.
 *
 * This is what the "Notifications" setting actually controls — the tunnel's own
 * ongoing notification is required by the foreground service and is not
 * optional. The toggle is read inside the worker rather than used to cancel it,
 * so turning notifications back on does not depend on re-scheduling work.
 */
class SubscriptionAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MoonlightApplication ?: return Result.success()
        val container = app.container

        val settings = container.settingsStore.settings.first()
        if (!settings.notificationsEnabled) return Result.success()

        // Refresh first: alerting on a stale cache would warn about a
        // subscription the user has already renewed.
        container.subscriptionRepository.refresh()

        val info = container.subscriptionRepository.subscription.value?.userInfo
            ?: return Result.success()

        val now = System.currentTimeMillis() / 1000
        val days = info.daysLeft(now)
        val usedFraction = info.usedFraction

        val message = when {
            info.isExpired(now) -> applicationContext.getString(R.string.alert_expired)
            days != null && days <= EXPIRY_WARNING_DAYS ->
                applicationContext.resources.getQuantityString(R.plurals.alert_expiring, days, days)
            usedFraction != null && usedFraction >= TRAFFIC_WARNING_FRACTION ->
                applicationContext.getString(R.string.alert_traffic_low)
            else -> null
        } ?: return Result.success()

        SubscriptionAlerts.notify(applicationContext, message)
        return Result.success()
    }

    private companion object {
        const val EXPIRY_WARNING_DAYS = 3
        const val TRAFFIC_WARNING_FRACTION = 0.9f
    }
}

object SubscriptionAlerts {

    private const val CHANNEL_ID = "moonlight_alerts"
    private const val NOTIFICATION_ID = 2001
    private const val WORK_NAME = "moonlight_subscription_alerts"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SubscriptionAlertWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // KEEP, so an app restart does not reset the interval and cause a
            // burst of checks.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.alert_channel_description)
            },
        )
    }

    fun notify(context: Context, message: String) {
        // Checked inline rather than via a helper so lint can see the guard.
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(vpn.moonlight.core.R.drawable.ml_ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
