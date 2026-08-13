package vpn.moonlight.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vpn.moonlight.core.tunnel.TunnelCounters
import vpn.moonlight.data.model.ConnectionState

/**
 * The single source of truth for tunnel state.
 *
 * Deliberately a process-wide object rather than something reached over a
 * binder: the service and the UI live in the same process, and a `StateFlow`
 * here is both simpler and harder to get wrong than a bound-service connection
 * that has to be re-established every time the activity restarts.
 */
object TunnelState {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _counters = MutableStateFlow(TunnelCounters(0, 0, 0, 0))

    /** Cumulative byte and packet counters for the current session. */
    val counters: StateFlow<TunnelCounters> = _counters.asStateFlow()

    private val _socksPort = MutableStateFlow<Int?>(null)

    /**
     * The port the running core's SOCKS inbound listens on. Exposed so the latency
     * probe can measure the active node through the live tunnel — this app is
     * excluded from its own tunnel, so it can reach the loopback proxy directly.
     */
    val socksPort: StateFlow<Int?> = _socksPort.asStateFlow()

    internal fun updateSocksPort(port: Int?) {
        _socksPort.value = port
    }

    internal fun update(state: ConnectionState) {
        _state.value = state
        if (state !is ConnectionState.Connected) {
            _counters.value = TunnelCounters(0, 0, 0, 0)
            if (!state.isActive) _socksPort.value = null
        }
    }

    internal fun updateCounters(counters: TunnelCounters) {
        _counters.value = counters
    }
}
