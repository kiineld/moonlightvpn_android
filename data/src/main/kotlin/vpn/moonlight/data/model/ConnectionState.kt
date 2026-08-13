package vpn.moonlight.data.model

/**
 * The tunnel's state, owned by the VPN service and observed everywhere else.
 *
 * [Connected.sinceEpochMillis] is a timestamp rather than an elapsed counter so
 * the session timer survives the UI process being killed and restarted.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val nodeId: String?) : ConnectionState
    data class Connected(val nodeId: String, val sinceEpochMillis: Long) : ConnectionState
    data class Reconnecting(val nodeId: String?, val attempt: Int) : ConnectionState
    data class Error(val reason: ConnectionError, val detail: String? = null) : ConnectionState

    val isActive: Boolean
        get() = this is Connecting || this is Connected || this is Reconnecting

    val nodeIdOrNull: String?
        get() = when (this) {
            is Connecting -> nodeId
            is Connected -> nodeId
            is Reconnecting -> nodeId
            else -> null
        }
}

/** Typed failures, so the UI can say what actually went wrong. */
enum class ConnectionError {
    NoSubscription,
    NoNodes,
    PermissionDenied,
    SubscriptionExpired,
    TrafficExhausted,
    DeviceLimitReached,
    CoreStartFailed,
    TunnelStartFailed,
    Network,
    Unknown,
}
