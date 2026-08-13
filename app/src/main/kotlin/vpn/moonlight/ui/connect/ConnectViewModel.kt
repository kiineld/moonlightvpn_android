package vpn.moonlight.ui.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vpn.moonlight.AppContainer
import vpn.moonlight.core.VpnController
import vpn.moonlight.data.model.ConnectionState
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.NodeSelection
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.repository.RefreshState
import vpn.moonlight.data.util.Formatters

/** One row in the node list. */
data class NodeRow(
    val node: ServerNode,
    val latency: Latency,
    val isSelected: Boolean,
)

data class ConnectUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val nodes: List<NodeRow> = emptyList(),
    val activeNode: ServerNode? = null,
    val isAuto: Boolean = true,
    val sessionSeconds: Long = 0L,
    val sessionBytes: Long = 0L,
    val daysLeft: Int? = null,
    val isRefreshing: Boolean = false,
    val isMeasuring: Boolean = false,
    val isListExpanded: Boolean = false,
    val hasSubscription: Boolean = false,
    val quotaRemainingFraction: Float? = null,
    val isPerpetual: Boolean = false,
) {
    val timerText: String get() = Formatters.formatDuration(sessionSeconds)

    /**
     * The dial sweep shows how much of the traffic quota is still available, so a
     * healthy subscription reads as a nearly full ring and drains as it is used.
     * Chosen over an animated or arbitrary value because the ring is the largest
     * element on the screen and should mean something.
     *
     * With no quota to report (unlimited, or a panel that does not say), a
     * connected tunnel shows a full ring and a stopped one shows none.
     */
    val progress: Float
        get() = when {
            !connection.isActive -> 0f
            quotaRemainingFraction != null -> quotaRemainingFraction
            connection is ConnectionState.Connected -> 1f
            else -> 0.08f
        }
}

class ConnectViewModel(private val container: AppContainer) : ViewModel() {

    private val listExpanded = MutableStateFlow(false)

    /** Ticks only while a session is up, so an idle screen does not wake every second. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val state: StateFlow<ConnectUiState> = combine(
        container.settingsStore.settings,
        container.subscriptionRepository.subscription,
        container.latencyRepository.latencies,
        container.latencyRepository.measuring,
        VpnController.state,
    ) { settings, subscription, latencies, measuring, connection ->
        val nodes = subscription?.nodes.orEmpty()
        val isAuto = settings.selection is NodeSelection.Auto
        val active = container.subscriptionRepository.resolve(settings.selection)

        ConnectUiState(
            connection = connection,
            nodes = nodes.map { node ->
                NodeRow(
                    node = node,
                    latency = latencies[node.id] ?: Latency.Unknown,
                    isSelected = !isAuto && node.id == active?.id,
                )
            },
            activeNode = active,
            isAuto = isAuto,
            isMeasuring = measuring,
            hasSubscription = subscription != null && nodes.isNotEmpty(),
            daysLeft = subscription?.userInfo?.daysLeft(System.currentTimeMillis() / 1000),
            quotaRemainingFraction = subscription?.userInfo?.usedFraction?.let { 1f - it },
            isPerpetual = subscription?.userInfo?.isPerpetual == true,
        )
    }
        .combine(listExpanded) { ui, expanded -> ui.copy(isListExpanded = expanded) }
        .combine(container.subscriptionRepository.refreshState) { ui, refresh ->
            ui.copy(isRefreshing = refresh is RefreshState.Refreshing)
        }
        .combine(ticker) { ui, now ->
            val connected = ui.connection as? ConnectionState.Connected
            ui.copy(
                sessionSeconds = connected
                    ?.let { Formatters.elapsedSeconds(it.sinceEpochMillis, now) }
                    ?: 0L,
            )
        }
        .combine(VpnController.counters) { ui, counters ->
            ui.copy(sessionBytes = if (ui.connection is ConnectionState.Connected) counters.totalBytes else 0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectUiState())

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
     * A full pass needs the core to itself, so while the tunnel is up only the
     * active node is measured — through the live proxy, which is the warm number
     * that actually matters. Other rows keep their last measurement.
     */
    fun measureLatency() {
        if (container.latencyRepository.measuring.value) return
        viewModelScope.launch {
            val activePort = VpnController.socksPort.value
            val activeNodeId = VpnController.state.value.nodeIdOrNull

            if (activePort != null && activeNodeId != null) {
                container.latencyProbe.measureActive(activeNodeId, activePort)
            } else {
                val nodes = container.subscriptionRepository.subscription.value?.nodes.orEmpty()
                container.latencyProbe.measureAll(nodes)
            }
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
