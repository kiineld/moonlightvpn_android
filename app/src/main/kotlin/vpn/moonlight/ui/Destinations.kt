package vpn.moonlight.ui

/** Every screen in the app. The first three are tab roots; the rest are pushed. */
enum class Destination(val route: String) {
    Onboarding("onboarding"),
    Import("import"),
    Connect("connect"),
    Subscription("subscription"),
    Settings("settings"),
    SplitTunnel("split"),
    Logs("logs"),
    ;

    companion object {
        /** Tab order, which also fixes the position of the sliding puck. */
        val tabs = listOf(Connect, Subscription, Settings)

        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}
