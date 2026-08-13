package vpn.moonlight.data.util

import java.util.Locale
import kotlin.math.abs

/** Byte magnitudes. The UI supplies the localised unit label for each. */
enum class ByteUnit { Bytes, Kilobytes, Megabytes, Gigabytes, Terabytes }

/** A size split into a rounded number and its unit, ready for localised assembly. */
data class FormattedSize(val value: Double, val unit: ByteUnit, val text: String)

object Formatters {

    private const val STEP = 1024.0

    /**
     * Scales bytes and picks a precision that keeps the figure short, matching
     * the design's "1,24 GB", "24,8 GB", "100 GB" progression: two decimals
     * under 10, one under 100, none above.
     */
    fun formatSize(bytes: Long, locale: Locale = Locale.getDefault()): FormattedSize {
        val safe = if (bytes < 0) 0L else bytes
        var value = safe.toDouble()
        var unitIndex = 0
        while (value >= STEP && unitIndex < ByteUnit.entries.lastIndex) {
            value /= STEP
            unitIndex++
        }
        val unit = ByteUnit.entries[unitIndex]
        val decimals = when {
            unit == ByteUnit.Bytes -> 0
            abs(value) < 10 -> 2
            abs(value) < 100 -> 1
            else -> 0
        }
        return FormattedSize(value, unit, String.format(locale, "%.${decimals}f", value))
    }

    /** `HH:MM:SS`, the session timer under the dial. Hours are not capped at 24. */
    fun formatDuration(totalSeconds: Long): String {
        val safe = if (totalSeconds < 0) 0L else totalSeconds
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /** Elapsed whole seconds since a start timestamp, floored at zero. */
    fun elapsedSeconds(sinceEpochMillis: Long, nowEpochMillis: Long): Long =
        ((nowEpochMillis - sinceEpochMillis) / 1000).coerceAtLeast(0L)

    /** Masks the middle of a subscription URL for display without hiding its shape. */
    fun shortenUrl(url: String, keepTail: Int = 8): String {
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val slash = withoutScheme.indexOf('/')
        if (slash < 0) return withoutScheme
        val host = withoutScheme.substring(0, slash)
        val path = withoutScheme.substring(slash + 1)
        return if (path.length <= keepTail) "$host/$path" else "$host/…${path.takeLast(keepTail)}"
    }
}
