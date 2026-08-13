package vpn.moonlight.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode

/**
 * Latency measurements, held apart from the node list so a probe pass never
 * rewrites the subscription. Written by the probe in `:vpn`, read by the UI.
 */
class LatencyRepository {

    private val _latencies = MutableStateFlow<Map<String, Latency>>(emptyMap())
    val latencies: StateFlow<Map<String, Latency>> = _latencies.asStateFlow()

    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    fun latencyOf(nodeId: String): Latency = _latencies.value[nodeId] ?: Latency.Unknown

    fun markMeasuring(nodeIds: Collection<String>) {
        _measuring.value = true
        _latencies.update { current ->
            current + nodeIds.associateWith { Latency.Measuring }
        }
    }

    fun publish(results: Map<String, Latency>) {
        _latencies.update { it + results }
        _measuring.value = false
    }

    /** Drops every measurement, e.g. when the subscription they belonged to is gone. */
    fun clear() {
        _latencies.value = emptyMap()
        _measuring.value = false
    }

    fun markIdle() {
        _measuring.value = false
        _latencies.update { current ->
            current.mapValues { (_, value) ->
                if (value is Latency.Measuring) Latency.Unknown else value
            }
        }
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
