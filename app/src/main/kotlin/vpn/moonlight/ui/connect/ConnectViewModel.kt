package vpn.moonlight.ui.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vpn.moonlight.AppContainer
import vpn.moonlight.core.VpnController
import vpn.moonlight.data.model.ConnectionState
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.NodeSelection
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.repository.RefreshState

/** One row in the node list. */
data class NodeRow(
    val node: ServerNode,
    /** The last measurement, which stands until a newer one replaces it. */
    val latency: Latency,
    /** A probe is out for this node right now. */
    val isMeasuring: Boolean,
    val isSelected: Boolean,
)

/**
 * Everything on the connect screen that changes when something *happens*.
 *
 * What changes every second — the connection time, the bytes this session — is
 * deliberately not here. Both used to be: a ticker rebuilt this whole state
 * once a second, connected or not, and the screen recomposed from the top with
 * it, server list included, for as long as it was open. The time is now worked
 * out by the one label that shows it, from [connectedSinceMillis], and the
 * bytes arrive on [ConnectViewModel.sessionBytes] for the one cell that shows
 * them.
 */
data class ConnectUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val nodes: List<NodeRow> = emptyList(),
    /** The node the tunnel is on — or, with it down, the one it would connect to. */
    val activeNode: ServerNode? = null,
    val activeLatency: Latency = Latency.Unknown,
    val isActiveMeasuring: Boolean = false,
    val isAuto: Boolean = true,
    val daysLeft: Int? = null,
    val isRefreshing: Boolean = false,
    val isMeasuring: Boolean = false,
    val isListExpanded: Boolean = false,
    val hasSubscription: Boolean = false,
    val isPerpetual: Boolean = false,
) {
    /** When the tunnel came up, or null while it is not. */
    val connectedSinceMillis: Long?
        get() = (connection as? ConnectionState.Connected)?.sinceEpochMillis

    /**
     * Whether the list needs an Auto row of its own. When the panel publishes
     * an "Auto" node, that row *is* the automatic choice — a second one beside
     * it would be two rows doing the same job.
     */
    val showsAutoRow: Boolean get() = nodes.isNotEmpty() && nodes.none { it.node.isAutoNode }
}

class ConnectViewModel(private val container: AppContainer) : ViewModel() {

    private val listExpanded = MutableStateFlow(false)

    val state: StateFlow<ConnectUiState> = combine(
        container.settingsStore.settings,
        container.subscriptionRepository.subscription,
        container.latencyRepository.latencies,
        container.latencyRepository.pending,
        VpnController.state,
    ) { settings, subscription, latencies, pending, connection ->
        val nodes = subscription?.nodes.orEmpty()
        val selection = settings.selection
        val isAuto = selection is NodeSelection.Auto

        // While the tunnel is up, the node to show is the one it is on, not the
        // one the selection would resolve to now. They part ways the moment a
        // ping pass finishes with the tunnel up: `Auto` resolves to whichever
        // node is fastest by the new figures, and naming that one would have
        // the screen claim a server the traffic is not going through.
        val inUse = connection.nodeIdOrNull?.let { id -> nodes.firstOrNull { it.id == id } }
        val active = inUse ?: container.subscriptionRepository.resolve(selection)

        ConnectUiState(
            connection = connection,
            nodes = nodes.map { node ->
                NodeRow(
                    node = node,
                    latency = latencies[node.id] ?: Latency.Unknown,
                    isMeasuring = node.id in pending,
                    isSelected = selection is NodeSelection.Pinned && node.id == selection.nodeId,
                )
            },
            activeNode = active,
            activeLatency = active?.let { latencies[it.id] } ?: Latency.Unknown,
            isActiveMeasuring = active != null && active.id in pending,
            isAuto = isAuto,
            isMeasuring = pending.isNotEmpty(),
            hasSubscription = subscription != null && nodes.isNotEmpty(),
            daysLeft = subscription?.userInfo?.daysLeft(System.currentTimeMillis() / 1000),
            isPerpetual = subscription?.userInfo?.isPerpetual == true,
        )
    }
        .combine(listExpanded) { ui, expanded -> ui.copy(isListExpanded = expanded) }
        .combine(container.subscriptionRepository.refreshState) { ui, refresh ->
            ui.copy(isRefreshing = refresh is RefreshState.Refreshing)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectUiState())

    /**
     * Bytes through the tunnel this session.
     *
     * Collecting this is what makes the service poll the tunnel for it, so it is
     * shared only while something is subscribed: with the screen off or the app
     * in the background nothing asks, and nothing polls.
     */
    val sessionBytes: StateFlow<Long> = VpnController.counters
        .map { it.totalBytes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun toggleConnection(context: Context) = VpnController.toggle(context)

    fun connect(context: Context) = VpnController.connect(context)

    fun toggleList() {
        listExpanded.value = !listExpanded.value
    }

    fun selectNode(nodeId: String) {
        viewModelScope.launch {
            container.settingsStore.setSelection(NodeSelection.Pinned(nodeId))
            listExpanded.value = false
        }
    }

    fun selectAuto() {
        viewModelScope.launch {
            container.settingsStore.setSelection(NodeSelection.Auto)
            listExpanded.value = false
        }
    }

    /**
     * Measures every node, tunnel up or down — the probe runs beside the tunnel
     * rather than through it. Each row keeps the number it had until its new one
     * lands.
     */
    fun measureLatency() {
        if (container.latencyRepository.pending.value.isNotEmpty()) return
        viewModelScope.launch {
            val nodes = container.subscriptionRepository.subscription.value?.nodes.orEmpty()
            container.latencyProbe.measureAll(nodes)
        }
    }

    fun refresh() {
        viewModelScope.launch { container.subscriptionRepository.refresh() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConnectViewModel(container) }
        }
    }
}
