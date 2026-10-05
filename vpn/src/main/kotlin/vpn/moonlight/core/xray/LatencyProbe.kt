package vpn.moonlight.core.xray

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.repository.LatencyRepository

/**
 * Measures every node, whether or not the tunnel is up.
 *
 * Each node is timed by the core's own probe ([XrayCore.pingBatch]), which runs
 * in an instance separate from the one carrying the tunnel. That is the whole
 * difference from what this used to be: the probe borrowed the single global
 * core, so a full pass could only run with the tunnel down, and once connected
 * the Ping button measured the one node in use and left every other row as it
 * was — blank, usually, since nothing was kept between launches either.
 *
 * A node's result is published the moment it lands, and replaces that node's
 * previous one; nothing is blanked while the pass is out. Nodes are measured
 * side by side, but only [concurrency] at a time: a subscription with sixty
 * nodes would otherwise open sixty handshakes at once on a phone's radio and
 * measure congestion instead of latency.
 */
class LatencyProbe(
    private val xray: XrayCore,
    private val latencies: LatencyRepository,
    /** Where a node's outbounds are written for the core to read. App-private. */
    private val workDir: File,
    private val url: String = PROBE_URL,
    private val timeoutSeconds: Int = PROBE_TIMEOUT_SECONDS,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) {
    /** One pass at a time: a second one would only double the handshakes. */
    private val pass = Mutex()

    suspend fun measureAll(nodes: List<ServerNode>): Result<Unit> {
        if (nodes.isEmpty()) return Result.failure(XrayException("no nodes to measure"))
        if (!pass.tryLock()) return Result.failure(XrayException("a latency pass is already running"))

        latencies.markMeasuring(nodes.map { it.id })
        return try {
            val answered = withContext(Dispatchers.IO) {
                // Left behind only if the process died mid-pass.
                workDir.listFiles()?.forEach { it.delete() }
                workDir.mkdirs()

                val slots = Semaphore(concurrency)
                coroutineScope {
                    nodes.mapIndexed { index, node ->
                        async { slots.withPermit { measure(index, node) } }
                    }.awaitAll().count { it }
                }
            }
            MoonlightLog.i(TAG, "latency pass: $answered of ${nodes.size} nodes answered")
            if (answered > 0) Result.success(Unit) else Result.failure(XrayException("no node answered"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Throwable) {
            // A pass is a background convenience; nothing it can hit is worth
            // taking the caller's scope down with it.
            MoonlightLog.w(TAG, "latency pass failed", failed)
            Result.failure(failed)
        } finally {
            withContext(NonCancellable) {
                latencies.markIdle()
                latencies.persist()
            }
            pass.unlock()
        }
    }

    /** Times one node and publishes the result. True if it answered. */
    private fun measure(index: Int, node: ServerNode): Boolean {
        val best = runCatching { bestDelay(index, node) }
            .onFailure { MoonlightLog.w(TAG, "could not probe ${node.name}: ${it.message}") }
            .getOrNull()

        latencies.publish(
            mapOf(node.id to (best?.let { Latency.Value(it) } ?: Latency.Failed("unreachable"))),
        )
        return best != null
    }

    /** The fastest of the node's outbounds, or null if none of them answered. */
    private fun bestDelay(index: Int, node: ServerNode): Int? {
        val config = pingConfigFor(node)

        // The file holds the node's credentials, so it lives in the app's own
        // cache for exactly as long as the core is reading it.
        val file = File(workDir, "node-$index.json")
        file.writeText(config.json)
        return try {
            val targets = config.tags.map { PingTarget(file.absolutePath, it) }
            xray.pingBatch(targets, timeoutSeconds, url).getOrThrow().filterNotNull().minOrNull()
        } finally {
            file.delete()
        }
    }

    private fun pingConfigFor(node: ServerNode): PingConfig {
        node.panelConfigJson?.let { return XrayConfigBuilder.pingConfig(it) }

        val link = node.shareLink
            ?: throw XrayConfigException("node has neither a config nor a link")
        return XrayConfigBuilder.pingConfigFromShareLink(xray.convertShareLink(link).getOrThrow())
    }

    companion object {
        private const val TAG = "LatencyProbe"

        /**
         * Cloudflare's captive-portal endpoint over **http**, not https: the probe
         * is timing the path to the node, and a TLS handshake to the target adds a
         * round trip that has nothing to do with it. It answers `204` with an
         * empty body from a global anycast address, so the number is about the
         * node rather than about which continent the target happens to be on.
         *
         * The same target, and the same timeout, as the macOS client — so one
         * server reads the same on both.
         */
        const val PROBE_URL = "http://cp.cloudflare.com/generate_204"

        /** Past this a node is too slow to be worth choosing, and reads `n/a`. */
        const val PROBE_TIMEOUT_SECONDS = 5

        /**
         * Wide enough that a dead node, which holds its slot for the full
         * timeout, does not stall the live ones behind it.
         */
        const val DEFAULT_CONCURRENCY = 8
    }
}
