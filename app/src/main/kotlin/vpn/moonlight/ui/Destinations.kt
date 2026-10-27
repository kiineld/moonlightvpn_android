package vpn.moonlight.ui

/** Every screen behind sign-in. The first four are tab roots; the rest are pushed. */
enum class Destination(val route: String) {
    Connect("connect"),
    Home("home"),
    Wallet("wallet"),
    Settings("settings"),
    Plans("plans"),
    SplitTunnel("split"),
    Logs("logs"),
    ;

    companion object {
        /** Tab order, which also fixes the position of the sliding puck. */
        val tabs = listOf(Connect, Home, Wallet, Settings)

        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}
