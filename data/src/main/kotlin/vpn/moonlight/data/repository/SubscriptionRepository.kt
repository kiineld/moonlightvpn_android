package vpn.moonlight.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vpn.moonlight.data.local.SubscriptionStore
import vpn.moonlight.data.model.NodeSelection
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.model.Subscription
import vpn.moonlight.data.remote.SubscriptionApi

/** Progress of a manual refresh, so the UI can show the design's spinner and "just now". */
sealed interface RefreshState {
    data object Idle : RefreshState
    data object Refreshing : RefreshState
    data class Done(val atEpochMillis: Long) : RefreshState
    data class Failed(val error: Throwable) : RefreshState
}

/**
 * Owns the current subscription: the disk cache is the source of truth for what
 * the UI shows, and a fetch replaces it only on success — a failed refresh must
 * never leave the user with no nodes.
 */
class SubscriptionRepository(
    private val api: SubscriptionApi,
    private val store: SubscriptionStore,
    private val latencies: LatencyRepository,
) {
    val subscription: StateFlow<Subscription?> = store.subscription

    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    suspend fun loadCached(): Subscription? = store.load()

    /** Adds or replaces the subscription from a pasted or scanned URL. */
    suspend fun import(url: String): Result<Subscription> = fetch(url)

    /** Re-fetches the stored subscription URL. */
    suspend fun refresh(): Result<Subscription> {
        val url = subscription.value?.url
            ?: return Result.failure(IllegalStateException("no subscription to refresh"))
        return fetch(url)
    }

    private suspend fun fetch(url: String): Result<Subscription> {
        _refreshState.value = RefreshState.Refreshing
        val result = api.fetch(url)
        result.fold(
            onSuccess = {
                store.save(it)
                _refreshState.value = RefreshState.Done(System.currentTimeMillis())
            },
            onFailure = { _refreshState.value = RefreshState.Failed(it) },
        )
        return result
    }

    suspend fun clear() {
        store.clear()
        _refreshState.value = RefreshState.Idle
    }

    /**
     * Resolves a selection to a concrete node. A pinned node that has vanished
     * from the subscription falls back to `Auto` rather than failing to connect.
     */
    fun resolve(selection: NodeSelection): ServerNode? {
        val nodes = subscription.value?.nodes ?: return null
        if (nodes.isEmpty()) return null
        return when (selection) {
            NodeSelection.Auto -> latencies.autoPick(nodes)
            is NodeSelection.Pinned -> nodes.firstOrNull { it.id == selection.nodeId }
                ?: latencies.autoPick(nodes)
        }
    }
}
