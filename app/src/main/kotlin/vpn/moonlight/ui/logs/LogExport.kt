package vpn.moonlight.ui.logs

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import vpn.moonlight.BuildConfig
import vpn.moonlight.core.xray.XrayLogFile
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.ConnectionState
import vpn.moonlight.data.model.Subscription

/**
 * Assembles a diagnostics bundle: environment, the app's log, and xray-core's own
 * log.
 *
 * Shared as a file rather than as intent text, because a debug-level core log runs
 * well past what an Intent extra can carry.
 */
object LogExport {

    private const val FILE_NAME = "moonlight-diagnostics.txt"

    fun build(
        context: Context,
        subscription: Subscription?,
        state: ConnectionState,
        coreVersion: String,
        verbose: Boolean,
    ): String = buildString {
        appendLine("=== Moonlight diagnostics ===")
        appendLine("app          ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
        appendLine("android      ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("device       ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("abi          ${Build.SUPPORTED_ABIS.firstOrNull()}")
        appendLine("xray-core    $coreVersion")
        appendLine("tun2socks    ${vpn.moonlight.core.BuildConfig.TUN2SOCKS_VERSION}")
        appendLine("state        ${state.describe()}")
        appendLine("verbose      ${if (verbose) "on" else "off"}")

        // Host only. The path of a subscription URL is a credential.
        appendLine("panel        ${subscription?.url?.hostOnly() ?: "none"}")
        appendLine("nodes        ${subscription?.nodes?.size ?: 0}")
        appendLine("selected     ${subscription?.nodes?.firstOrNull { it.id == state.nodeIdOrNull }?.name ?: "—"}")
        appendLine("geodata      ${geodataSummary(context)}")
        appendLine()

        val entries = MoonlightLog.snapshot()
        appendLine("--- app log (${entries.size} lines) ---")
        if (entries.isEmpty()) appendLine("(empty)") else entries.forEach { appendLine(it.format()) }
        appendLine()

        val core = XrayLogFile.tail(context)
        appendLine("--- xray-core log ---")
        appendLine(
            if (core.isBlank()) {
                "(empty — the core has not run since the last connect attempt)"
            } else {
                core
            },
        )
    }

    /** Writes the bundle to cache and returns a share intent for it. */
    fun shareIntent(context: Context, contents: String): Intent {
        val file = File(context.cacheDir, FILE_NAME).apply { writeText(contents) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Moonlight diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun ConnectionState.describe(): String = when (this) {
        ConnectionState.Disconnected -> "disconnected"
        is ConnectionState.Connecting -> "connecting"
        is ConnectionState.Connected -> "connected"
        is ConnectionState.Reconnecting -> "reconnecting (attempt $attempt)"
        is ConnectionState.Error -> "error: $reason ${detail.orEmpty()}"
    }

    private fun String.hostOnly(): String =
        removePrefix("https://").removePrefix("http://").substringBefore('/')

    private fun geodataSummary(context: Context): String {
        val dir = vpn.moonlight.core.xray.XrayAssets.directory(context)
        return listOf("geoip.dat", "geosite.dat").joinToString(", ") { name ->
            val length = File(dir, name).length()
            if (length == 0L) "$name missing" else "$name ${length / 1024 / 1024} MB"
        }
    }
}
