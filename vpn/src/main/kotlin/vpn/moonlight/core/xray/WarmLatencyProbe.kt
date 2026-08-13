package vpn.moonlight.core.xray

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.repository.LatencyRepository

/** Why a full measurement pass could not run. */
sealed interface ProbeRefusal {
    /** The tunnel is up, or took the core mid-pass. One running core per process. */
    data object TunnelActive : ProbeRefusal
    data object NoNodes : ProbeRefusal
}

class ProbeRefusedException(val reason: ProbeRefusal) : Exception(reason.toString())

/**
 * Measures node latency with the connection already established.
 *
 * Nodes are measured in **batches through a single core instance**: each node in
 * the batch gets its own SOCKS inbound pinned to its own outbound, so their
 * measurements run at the same time. The core, not the network, was the reason
 * this used to be serial — libXray allows one global instance, so a node at a
 * time meant a core start and teardown per node.
 *
 * A pass only runs while the tunnel is down. While it is up, [measureActive]
 * measures the node actually in use through the live proxy, which is both warm
 * and the number that matters.
 *
 * Results are published per node as they land, so rows fill in progressively.
 */
class WarmLatencyProbe(
    private val xray: XrayCore,
    private val latencies: LatencyRepository,
    private val transport: ProbeTransport = OkHttpProbeTransport(),
    private val assetDir: String? = null,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {

    private class Entry(val node: ServerNode, val port: Int, val configJson: String)

    suspend fun measureAll(nodes: List<ServerNode>): Result<Unit> {
        if (nodes.isEmpty()) {
            return Result.failure(ProbeRefusedException(ProbeRefusal.NoNodes))
        }
        // The tunnel's claim is the signal, deliberately not `xray.isRunning()`:
        // a core running with nobody claiming it is a leak from an earlier failure,
        // and refusing to probe would leave it stuck that way. The defensive stop
        // before each start clears it instead.
        if (XrayCoreOwner.isTunnelClaiming) {
            return Result.failure(ProbeRefusedException(ProbeRefusal.TunnelActive))
        }

        latencies.markMeasuring(nodes.map { it.id })
        return coroutineScope {
            val pass = async(Dispatchers.IO) { runPass(nodes) }
            // A connect must never queue behind a measurement: claiming the core
            // cancels the pass where it stands, including its in-flight requests.
            XrayCoreOwner.onTunnelClaim { pass.cancel(CancellationException("tunnel claimed the core")) }
            try {
                pass.await()
            } catch (cancelled: CancellationException) {
                // Distinguish "the tunnel took the core" from "the caller went
                // away"; only the first is an answer this function can return.
                if (!isActive) throw cancelled
                MoonlightLog.i(TAG, "latency pass stood down for the tunnel")
                Result.failure(ProbeRefusedException(ProbeRefusal.TunnelActive))
            } catch (failed: Throwable) {
                // A pass is a background convenience; nothing it can hit is worth
                // taking the caller's scope down with it.
                MoonlightLog.w(TAG, "latency pass failed", failed)
                Result.failure(failed)
            } finally {
                XrayCoreOwner.onTunnelClaim(null)
                withContext(NonCancellable) { latencies.markIdle() }
            }
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

    private suspend fun runPass(nodes: List<ServerNode>): Result<Unit> {
        var measured = 0
        for (batch in nodes.chunked(batchSize)) {
            coroutineContext.ensureActive()
            measured += measureBatch(batch)
        }
        MoonlightLog.i(TAG, "latency pass: $measured of ${nodes.size} nodes answered")
        return if (measured > 0) {
            Result.success(Unit)
        } else {
            Result.failure(XrayException("no node answered"))
        }
    }

    private suspend fun measureBatch(batch: List<ServerNode>): Int {
        val ports = portsFor(batch.size)
        val entries = batch.mapIndexedNotNull { index, node ->
            fullConfigFor(node)?.let { Entry(node, ports[index], it) }
        }

        val unusable = batch - entries.map { it.node }.toSet()
        if (unusable.isNotEmpty()) {
            latencies.publish(unusable.associate { it.id to Latency.Failed("config could not be built") })
        }
        if (entries.isEmpty()) return 0

        val merged = runCatching {
            XrayConfigBuilder.batchProbeConfig(
                entries.map { ProbeTarget(it.configJson, it.port) },
                XrayConfigOptions(socksPort = entries.first().port, assetDir = assetDir),
            )
        }.getOrElse {
            MoonlightLog.w(TAG, "batch config could not be built; measuring one at a time", it)
            null
        }

        if (merged != null) {
            val covered = merged.accepted.map { entries[it] }
            val together = measureTogether(merged.json, covered)
            if (together != null) {
                // Anything the merge left out still deserves a number.
                val leftOut = entries - covered.toSet()
                return together + leftOut.sumOf { measureOne(it) }
            }
        }
        return entries.sumOf { measureOne(it) }
    }

    /**
     * Runs a whole batch in one core instance. Null means the core refused the
     * merged config, which is the caller's cue to fall back to one at a time.
     */
    private suspend fun measureTogether(configJson: String, entries: List<Entry>): Int? =
        XrayCoreOwner.exclusive {
            // Defensive: clears an instance an earlier failure may have left behind,
            // which would otherwise make every later start fail as "already running".
            xray.stop()

            val started = xray.start(configJson)
            if (started.isFailure) {
                MoonlightLog.w(TAG, "batch of ${entries.size} rejected: ${started.exceptionOrNull()?.message}")
                xray.stop()
                return@exclusive null
            }

            try {
                coroutineScope {
                    entries.map { entry -> async { measureThroughPort(entry) } }.awaitAll().count { it }
                }
            } finally {
                xray.stop()
            }
        }

    /** The old path: one node, one core instance. Kept for when a merge will not do. */
    private suspend fun measureOne(entry: Entry): Int {
        val config = runCatching {
            XrayConfigBuilder.probeConfig(
                entry.configJson,
                XrayConfigOptions(socksPort = entry.port, assetDir = assetDir),
            )
        }.getOrNull() ?: run {
            latencies.publish(mapOf(entry.node.id to Latency.Failed("config could not be built")))
            return 0
        }

        val answered = XrayCoreOwner.exclusive {
            xray.stop()
            val started = xray.start(config)
            if (started.isFailure) {
                MoonlightLog.w(TAG, "probe rejected for ${entry.node.name}: ${started.exceptionOrNull()?.message}")
                xray.stop()
                latencies.publish(
                    mapOf(entry.node.id to Latency.Failed(started.exceptionOrNull()?.message)),
                )
                return@exclusive false
            }
            try {
                measureThroughPort(entry)
            } finally {
                xray.stop()
            }
        }
        return if (answered) 1 else 0
    }

    /** Times one node through its own inbound and publishes the result. */
    private suspend fun measureThroughPort(entry: Entry): Boolean {
        val latency = transport.warmRoundTripMs(entry.port)
            ?.let { Latency.Value(it.toInt()) }
            ?: Latency.Failed("unreachable")
        latencies.publish(mapOf(entry.node.id to latency))
        return latency is Latency.Value
    }

    private fun portsFor(count: Int): List<Int> =
        xray.freePorts(count).getOrNull()?.distinct()?.takeIf { it.size == count }
            ?: List(count) { FALLBACK_PORT + it }

    private fun fullConfigFor(node: ServerNode): String? =
        node.panelConfigJson
            ?: node.shareLink?.let { link -> xray.convertShareLink(link).getOrNull() }

    internal companion object {
        private const val TAG = "WarmLatencyProbe"
        const val FALLBACK_PORT = 10810

        /**
         * Nodes measured per core instance. Large enough that a typical
         * subscription is one or two passes, small enough that one bad batch
         * costs little and the ports stay in a sane range.
         */
        const val DEFAULT_BATCH_SIZE = 8
    }
}
