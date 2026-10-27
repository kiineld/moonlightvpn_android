package vpn.moonlight.deeplink


/** An action requested from outside the app. */
sealed interface DeepLink {
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
 * moonlight://connect
 * moonlight://disconnect
 * moonlight://open
 * ```
 *
 * `import` links from before the app had accounts only open it: subscriptions
 * now come from the account, and a link must not be a way to point the tunnel
 * at somebody else's servers.
 */
object DeepLinks {

    const val SCHEME = "moonlight"

    private const val SEPARATOR = "://"

    fun parse(raw: String?): DeepLink? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!text.startsWith("$SCHEME$SEPARATOR", ignoreCase = true)) return null

        val body = text.substring(SCHEME.length + SEPARATOR.length)
        val action = body.substringBefore('?').substringBefore('/').lowercase()

        return when (action) {
            "connect", "start" -> DeepLink.Connect
            "disconnect", "stop" -> DeepLink.Disconnect
            "open", "", "import", "add", "subscribe" -> DeepLink.Open
            else -> null
        }
    }
}
