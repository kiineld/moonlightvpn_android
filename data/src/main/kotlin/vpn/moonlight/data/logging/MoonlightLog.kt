package vpn.moonlight.data.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { Debug, Info, Warn, Error }

data class LogEntry(
    val atEpochMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    fun format(): String {
        val time = TIME_FORMAT.get()!!.format(Date(atEpochMillis))
        return "$time ${level.symbol} $tag: $message"
    }

    private val LogLevel.symbol: String
        get() = when (this) {
            LogLevel.Debug -> "D"
            LogLevel.Info -> "I"
            LogLevel.Warn -> "W"
            LogLevel.Error -> "E"
        }

    private companion object {
        val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }
    }
}

/**
 * The app's own log, kept in memory so it can be shown and shared from inside the
 * app.
 *
 * Reading logcat is not an option — Android has denied apps access to other
 * processes' logs since 4.1, and asking a user for a `adb logcat` dump is not a
 * support flow. So everything worth diagnosing is recorded here as well as being
 * forwarded to logcat for development.
 *
 * Every message passes through [LogRedactor] on the way in, not on the way out, so
 * a secret cannot linger in memory waiting for an export path that forgets to
 * redact.
 */
object MoonlightLog {

    private const val CAPACITY = 1500

    private val buffer = ArrayDeque<LogEntry>(CAPACITY)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun d(tag: String, message: String) = record(LogLevel.Debug, tag, message, null)
    fun i(tag: String, message: String) = record(LogLevel.Info, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) =
        record(LogLevel.Warn, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) =
        record(LogLevel.Error, tag, message, error)

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        _entries.value = emptyList()
    }

    fun snapshot(): List<LogEntry> = synchronized(buffer) { buffer.toList() }

    fun export(): String = snapshot().joinToString("\n") { it.format() }

    private fun record(level: LogLevel, tag: String, message: String, error: Throwable?) {
        val full = buildString {
            append(message)
            if (error != null) {
                append(" | ")
                append(error::class.java.simpleName)
                error.message?.let { append(": ").append(it) }
            }
        }
        val entry = LogEntry(
            atEpochMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = LogRedactor.redact(full),
        )

        val updated = synchronized(buffer) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
            buffer.toList()
        }
        _entries.value = updated

        when (level) {
            LogLevel.Debug -> Log.d(tag, full)
            LogLevel.Info -> Log.i(tag, full)
            LogLevel.Warn -> Log.w(tag, full, error)
            LogLevel.Error -> Log.e(tag, full, error)
        }
    }
}
