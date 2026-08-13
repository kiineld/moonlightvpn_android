package vpn.moonlight.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vpn.moonlight.data.logging.MoonlightLog

/**
 * Installs a downloaded APK through the system package installer.
 *
 * Uses a PackageInstaller **session** rather than an ACTION_VIEW intent on a
 * FileProvider URI. The intent route hands the installer a content URI it has
 * to open across a UID boundary, and the read grant does not reliably survive
 * the trip — the installer fails with a bare SecurityException and the user
 * sees nothing happen. A session takes the bytes directly, so there is no URI
 * to grant and the result comes back as a status we can log.
 */
object ApkInstaller {

    private const val TAG = "Update"

    /**
     * Streams [apk] into a session and commits it. The user still confirms in
     * the system dialog, which is also where Android offers to allow this app
     * as an install source if it is not one yet.
     */
    suspend fun install(context: Context, apk: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply { setAppPackageName(context.packageName) }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(WRITE_NAME, 0, apk.length()).use { sink ->
                    apk.inputStream().use { it.copyTo(sink) }
                    session.fsync(sink)
                }
                session.commit(statusReceiver(context).intentSender)
            }
            MoonlightLog.i(TAG, "committed install session $sessionId")
            true
        }.getOrElse {
            MoonlightLog.w(TAG, "could not start the install", it)
            false
        }
    }

    private fun statusReceiver(context: Context): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
        // Mutable because the installer fills in the status extras it sends back.
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /**
     * Whether [apk] is the same app, signed by the same key, as the one running.
     *
     * Android refuses an update signed by a different key and reports only "app
     * not installed". Checking first turns that into a message that explains
     * itself, and keeps a download that was tampered with in transit away from
     * the installer.
     */
    fun signedLikeInstalled(context: Context, apk: File): Boolean = runCatching {
        val pm = context.packageManager
        val downloaded = pm.getPackageArchiveInfo(apk.absolutePath, signingFlags())
        if (downloaded == null) {
            MoonlightLog.w(TAG, "could not read the downloaded apk")
            return@runCatching false
        }
        if (downloaded.packageName != context.packageName) {
            MoonlightLog.w(TAG, "downloaded apk is ${downloaded.packageName}, not ${context.packageName}")
            return@runCatching false
        }

        val ours = fingerprints(pm.getPackageInfo(context.packageName, signingFlags()))
        val theirs = fingerprints(downloaded)
        ours.isNotEmpty() && ours == theirs
    }.getOrElse {
        MoonlightLog.w(TAG, "signature check failed", it)
        false
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= 28) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

    @Suppress("DEPRECATION")
    private fun fingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().mapNotNull { it?.toByteArray() }.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private const val WRITE_NAME = "moonlight.apk"
}
