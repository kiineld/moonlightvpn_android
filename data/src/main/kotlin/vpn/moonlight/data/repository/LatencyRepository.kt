package vpn.moonlight.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vpn.moonlight.data.local.LatencyStore
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode

/**
 * Latency measurements, held apart from the node list so a probe pass never
 * rewrites the subscription. Written by the probe in `:vpn`, read by the UI.
 *
 * A measurement stays until a newer one for the same node replaces it. Starting
 * a pass does not blank the numbers on screen, a pass that is cut short leaves
 * the nodes it did not reach as they were, and with a [store] the numbers come
 * back after the process has been killed.
 */
class LatencyRepository(private val store: LatencyStore? = null) {

    private val _latencies = MutableStateFlow<Map<String, Latency>>(emptyMap())
    val latencies: StateFlow<Map<String, Latency>> = _latencies.asStateFlow()

    private val _pending = MutableStateFlow<Set<String>>(emptySet())

    /** The nodes a probe is out for right now. Their last measurement still stands. */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    private val restoreLock = Mutex()
    private var restored = false

    fun latencyOf(nodeId: String): Latency = _latencies.value[nodeId] ?: Latency.Unknown

    /**
     * Puts the stored measurements back. Safe to call from every place that might
     * be first to need them — the app starting, or the tunnel service being
     * started without it — because only the first call reads the disk.
     */
    suspend fun restore() {
        val store = store ?: return
        restoreLock.withLock {
            if (restored) return
            val saved = store.load()
            // Under anything measured since launch, never over it.
            _latencies.update { saved + it }
            restored = true
        }
    }

    fun markMeasuring(nodeIds: Collection<String>) {
        _pending.update { it + nodeIds }
    }

    fun publish(results: Map<String, Latency>) {
        _latencies.update { it + results }
        _pending.update { it - results.keys }
    }

    /** A pass is over, finished or not. Whatever it did not reach keeps its last value. */
    fun markIdle() {
        _pending.value = emptySet()
    }

    /** Writes the measurements to disk, at the end of a pass. */
    suspend fun persist() {
        store?.save(_latencies.value)
    }

    /**
     * Forgets the measurements of nodes the subscription no longer has —
     * otherwise every node a panel ever renamed would stay on disk for good.
     */
    suspend fun retainOnly(currentNodeIds: Collection<String>) {
        // First, or a later restore would bring the dropped ones straight back.
        restore()
        val keep = currentNodeIds.toSet()
        if (_latencies.value.keys.all { it in keep }) return
        _latencies.update { all -> all.filterKeys { it in keep } }
        persist()
    }

    /** Drops every measurement, e.g. when the subscription they belonged to is gone. */
    suspend fun clear() {
        _latencies.value = emptyMap()
        _pending.value = emptySet()
        store?.clear()
    }

    /**
     * What `Auto` resolves to: the panel's own "Auto" node when it publishes one,
     * otherwise the lowest measured latency. The panel knows more about node
     * health than a latency probe from a single phone does, so its own
     * load-balancing entry wins when present.
     */
    fun autoPick(nodes: List<ServerNode>): ServerNode? =
        nodes.firstOrNull { it.isAutoNode } ?: fastest(nodes)

    /** Lowest measured latency, falling back to the first node. */
    fun fastest(nodes: List<ServerNode>): ServerNode? {
        if (nodes.isEmpty()) return null
        val measured = nodes.mapNotNull { node ->
            (latencyOf(node.id) as? Latency.Value)?.let { node to it.ms }
        }
        return measured.minByOrNull { it.second }?.first ?: nodes.first()
    }
}
