package vpn.moonlight.core.xray

import android.content.Context
import java.io.File
import vpn.moonlight.data.logging.LogRedactor

/**
 * Where xray-core writes its own log.
 *
 * The core logs inside Go, so its output never passes through the app's logger.
 * Pointing `log.error` at a file is the only way to get at it: apps cannot read
 * logcat (denied since Android 4.1), and the whole point is that a user can send
 * these logs without a cable.
 *
 * Truncated before each connect rather than rotated. A support log wants the most
 * recent attempt, and an unbounded file on a phone is its own bug.
 */
object XrayLogFile {

    private const val DIR = "logs"
    private const val NAME = "xray.log"

    /** Read back at most this much, so one long-running session cannot dominate. */
    private const val MAX_EXPORT_BYTES = 256L * 1024

    /** How much of the tail the on-screen view shows. */
    private const val MAX_VIEW_LINES = 400

    fun file(context: Context): File =
        File(File(context.filesDir, DIR).apply { mkdirs() }, NAME)

    /** Hard cap. The core appends, so after a reset it simply continues from zero. */
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    fun truncate(context: Context) {
        runCatching { file(context).writeText("") }
    }

    /**
     * Keeps the file bounded while a session runs.
     *
     * At debug level the core writes megabytes a minute; without this a long
     * session would fill the device. Truncating a file the core holds open is
     * safe: its handle is append-mode, so writes resume at the new end.
     */
    fun trimIfTooLarge(context: Context) {
        val target = file(context)
        runCatching {
            if (target.length() > MAX_FILE_BYTES) {
                target.writeText("--- log truncated at ${MAX_FILE_BYTES / 1024 / 1024} MB ---\n")
            }
        }
    }

    /**
     * The tail of the core's log, redacted. Reads the last [MAX_EXPORT_BYTES] so a
     * long session does not have to be held in memory in full.
     */
    /** The most recent lines, for display in the log screen. */
    fun recentLines(context: Context): List<String> =
        tail(context).lineSequence().filter { it.isNotBlank() }.toList().takeLast(MAX_VIEW_LINES)

    fun tail(context: Context): String {
        val target = file(context)
        if (!target.exists() || target.length() == 0L) return ""

        val text = runCatching {
            if (target.length() <= MAX_EXPORT_BYTES) {
                target.readText()
            } else {
                target.inputStream().use { stream ->
                    stream.skip(target.length() - MAX_EXPORT_BYTES)
                    stream.readBytes().toString(Charsets.UTF_8).substringAfter('\n')
                }
            }
        }.getOrElse { return "" }

        return LogRedactor.redact(text)
    }
}
