package vpn.moonlight.deeplink

import java.net.URLDecoder

/** An action requested from outside the app. */
sealed interface DeepLink {
    /** Add a subscription, e.g. from the Telegram bot. */
    data class ImportSubscription(val url: String) : DeepLink

    data object Connect : DeepLink
    data object Disconnect : DeepLink
    data object Open : DeepLink
}

/**
 * Parses `moonlight://` links.
 *
 * Takes a raw string rather than an `android.net.Uri` so the whole grammar is
 * testable on the JVM — `Uri` is a framework class and would need an instrumented
 * test or a mock for what is ultimately string handling.
 *
 * Supported:
 * ```
 * moonlight://import?url=<subscription url>
 * moonlight://import?sub=<subscription url>
 * moonlight://import/<subscription url>
 * moonlight://connect
 * moonlight://disconnect
 * moonlight://open
 * ```
 */
object DeepLinks {

    const val SCHEME = "moonlight"

    private const val SEPARATOR = "://"

    fun parse(raw: String?): DeepLink? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!text.startsWith("$SCHEME$SEPARATOR", ignoreCase = true)) return null

        val body = text.substring(SCHEME.length + SEPARATOR.length)
        val withoutQuery = body.substringBefore('?')
        val query = body.substringAfter('?', "")

        val action = withoutQuery.substringBefore('/').lowercase()
        val pathRemainder = withoutQuery.substringAfter('/', "")

        return when (action) {
            "import", "add", "subscribe" -> importLink(query, pathRemainder)
            "connect", "start" -> DeepLink.Connect
            "disconnect", "stop" -> DeepLink.Disconnect
            "open", "" -> DeepLink.Open
            else -> null
        }
    }

    private fun importLink(query: String, pathRemainder: String): DeepLink? {
        val fromQuery = query.parameters()
            .firstNotNullOfOrNull { (key, value) ->
                value.takeIf { key in setOf("url", "sub", "subscription", "link") }
            }

        val candidate = (fromQuery ?: pathRemainder.decode()).trim()
        if (candidate.isEmpty()) return null

        // Only http(s): the import flow expects a subscription endpoint, and
        // accepting arbitrary schemes here would hand a link a way to point the
        // app at something that is not one.
        val normalised = when {
            candidate.startsWith("http://", ignoreCase = true) -> candidate
            candidate.startsWith("https://", ignoreCase = true) -> candidate
            candidate.contains('.') -> "https://$candidate"
            else -> return null
        }
        return DeepLink.ImportSubscription(normalised)
    }

    private fun String.parameters(): List<Pair<String, String>> =
        split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                pair.substring(0, separator).lowercase() to pair.substring(separator + 1).decode()
            }

    private fun String.decode(): String =
        runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
}
