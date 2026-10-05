package vpn.moonlight.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /**
     * Whether anything is reading [counters] — in practice, whether the connect
     * screen is on. The service polls the tunnel for them only while this is
     * true: a counter nobody is looking at is not worth waking the processor
     * for once a second, for as long as the tunnel is up.
     */
    internal val countersWatched: Flow<Boolean> =
        _counters.subscriptionCount.map { it > 0 }.distinctUntilChanged()

    internal fun update(state: ConnectionState) {
        _state.value = state
        if (state !is ConnectionState.Connected) {
            _counters.value = TunnelCounters(0, 0, 0, 0)
        }
    }

    internal fun updateCounters(counters: TunnelCounters) {
        _counters.value = counters
    }
}
