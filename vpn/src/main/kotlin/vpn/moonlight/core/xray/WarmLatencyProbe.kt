package vpn.moonlight.core.xray

import vpn.moonlight.data.logging.MoonlightLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.repository.LatencyRepository

/** Why a full measurement pass could not run. */
sealed interface ProbeRefusal {
    /** The tunnel is up; libXray allows only one running core per process. */
    data object TunnelActive : ProbeRefusal
    data object NoNodes : ProbeRefusal
}

class ProbeRefusedException(val reason: ProbeRefusal) : Exception(reason.toString())

/**
 * Measures node latency with the connection already established.
 *
 * Runs each node in its own short-lived core instance, sequentially, because
 * libXray keeps a single global instance — `RunXray` returns `ErrAlreadyRunning`
 * for a second one. That also means a full pass can only run while the tunnel is
 * down; while it is up, [measureActive] measures the node actually in use through
 * the live proxy, which is both warm and the number that matters.
 *
 * Results are published per node so rows fill in as they land.
 */
class WarmLatencyProbe(
    private val xray: XrayCore,
    private val latencies: LatencyRepository,
    private val transport: ProbeTransport = OkHttpProbeTransport(),
    private val assetDir: String? = null,
) {
    suspend fun measureAll(nodes: List<ServerNode>): Result<Unit> = withContext(Dispatchers.IO) {
        if (nodes.isEmpty()) {
            return@withContext Result.failure(ProbeRefusedException(ProbeRefusal.NoNodes))
        }
        // The tunnel's claim is the signal, deliberately not `xray.isRunning()`:
        // a core that is running with nobody claiming it is a leak from an earlier
        // failure, and refusing to probe would leave it stuck that way. The
        // defensive stop in measureIsolated clears it instead.
        if (XrayCoreOwner.isTunnelClaiming) {
            return@withContext Result.failure(ProbeRefusedException(ProbeRefusal.TunnelActive))
        }

        latencies.markMeasuring(nodes.map { it.id })
        var measured = 0

        for (node in nodes) {
            // A connect that arrives mid-pass must not have to wait for two dozen
            // nodes, so the pass gives up as soon as the tunnel wants the core.
            if (XrayCoreOwner.isTunnelClaiming) {
                latencies.markIdle()
                return@withContext Result.failure(ProbeRefusedException(ProbeRefusal.TunnelActive))
            }
            val latency = measureIsolated(node)
            latencies.publish(mapOf(node.id to latency))
            if (latency is Latency.Value) measured++
        }

        MoonlightLog.i(TAG, "latency pass: $measured of ${nodes.size} nodes answered")
        latencies.markIdle()
        if (measured > 0) {
            Result.success(Unit)
        } else {
            Result.failure(XrayException("no node answered"))
        }
    }

    /** Measures one node through an already-running tunnel's local proxy. */
    suspend fun measureActive(nodeId: String, socksPort: Int): Latency =
        withContext(Dispatchers.IO) {
            latencies.markMeasuring(listOf(nodeId))
            val latency = transport.warmRoundTripMs(socksPort)
                ?.let { Latency.Value(it.toInt()) }
                ?: Latency.Failed("unreachable")
            latencies.publish(mapOf(nodeId to latency))
            latencies.markIdle()
            latency
        }

    private suspend fun measureIsolated(node: ServerNode): Latency {
        val port = xray.freePorts(1).getOrNull()?.firstOrNull() ?: FALLBACK_PORT
        val config = probeConfigFor(node, port)
            ?: return Latency.Failed("config could not be built")

        return XrayCoreOwner.exclusive {
            // Defensive: clears an instance an earlier failure may have left behind,
            // which would otherwise make every later start fail as "already running".
            xray.stop()

            val started = xray.start(config)
            if (started.isFailure) {
                MoonlightLog.w(TAG, "probe rejected for ${node.name}: ${started.exceptionOrNull()?.message}")
                xray.stop()
                return@exclusive Latency.Failed(started.exceptionOrNull()?.message)
            }

            try {
                transport.warmRoundTripMs(port)
                    ?.let { Latency.Value(it.toInt()) }
                    ?: Latency.Failed("unreachable")
            } finally {
                xray.stop()
            }
        }
    }

    private fun probeConfigFor(node: ServerNode, port: Int): String? {
        val options = XrayConfigOptions(socksPort = port, assetDir = assetDir)
        val full = node.panelConfigJson
            ?: node.shareLink?.let { link -> xray.convertShareLink(link).getOrNull() }
            ?: return null
        return runCatching { XrayConfigBuilder.probeConfig(full, options) }.getOrNull()
    }

    internal companion object {
        private const val TAG = "WarmLatencyProbe"
        const val FALLBACK_PORT = 10810
    }
}
