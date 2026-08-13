package vpn.moonlight.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import vpn.moonlight.data.logging.MoonlightLog

/**
 * Receives the outcome of an install session.
 *
 * The first thing a session reports is almost always STATUS_PENDING_USER_ACTION:
 * the installer will not touch anything until the user confirms, and it hands
 * back the dialog to launch. Without this step the commit looks like it silently
 * did nothing.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    MoonlightLog.w(TAG, "installer asked for confirmation but sent no intent")
                    return
                }
                // Started from a receiver, so it needs its own task.
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { MoonlightLog.w(TAG, "could not show the install prompt", it) }
            }

            PackageInstaller.STATUS_SUCCESS ->
                MoonlightLog.i(TAG, "update installed")

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                MoonlightLog.w(TAG, "install finished with status $status: ${message.orEmpty()}")
            }
        }
    }

    private companion object {
        const val TAG = "Update"
    }
}
