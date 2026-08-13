package vpn.moonlight.core.xray

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.repository.LatencyRepository

class WarmLatencyProbeTest {

    @After
    fun releaseClaim() = XrayCoreOwner.releaseTunnelClaim()

    /** Records the order of core calls so start/stop pairing can be asserted. */
    private open class RecordingCore(protected var running: Boolean = false) : XrayCore {
        val calls = mutableListOf<String>()

        override fun version() = "26.7.28"
        override fun isRunning() = running

        override fun convertShareLink(shareLink: String) =
            Result.success("""{"outbounds":[{"tag":"proxy","protocol":"vless"}]}""")

        override fun start(configJson: String): Result<Unit> {
            calls += "start"
            // Mirrors libXray: a second instance is refused outright.
            if (running) return Result.failure(XrayException("xray is already running"))
            running = true
            return Result.success(Unit)
        }

        override fun stop(): Result<Unit> {
            calls += "stop"
            running = false
            return Result.success(Unit)
        }

        override fun freePorts(count: Int) = Result.success(List(count) { 10810 + it })
        override fun registerSocketProtector(protect: (Int) -> Boolean) = Unit
        override fun setDns(server: String, protect: (Int) -> Boolean) = Result.success(Unit)
        override fun resetDns() = Unit
    }

    private class FixedTransport(private val ms: Long?) : ProbeTransport {
        override suspend fun warmRoundTripMs(socksPort: Int) = ms
    }

    private fun nodes(count: Int) = (1..count).map {
        ServerNode(id = "n$it", name = "N$it", shareLink = "vless://n$it@h:443#N$it")
    }

    @Test
    fun `stops the core before every start, clearing a leaked instance`() {
        // The regression: a failed attempt left the core running, and every later
        // start failed with "xray is already running" until the process died.
        val core = RecordingCore()
        val probe = WarmLatencyProbe(core, LatencyRepository(), FixedTransport(42))

        runTest { probe.measureAll(nodes(2)) }

        // One batch, so one instance: the pairing is what matters, not the count.
        assertEquals(listOf("stop", "start", "stop"), core.calls)
        assertFalse("the core must not be left running", core.isRunning())
    }

    @Test
    fun `measures a batch of nodes in a single core instance`() {
        val core = RecordingCore()
        val latencies = LatencyRepository()
        val probe = WarmLatencyProbe(core, latencies, FixedTransport(21))

        runTest { probe.measureAll(nodes(6)) }

        assertEquals("six nodes should cost one start", 1, core.calls.count { it == "start" })
        (1..6).forEach { assertEquals(Latency.Value(21), latencies.latencyOf("n$it")) }
    }

    @Test
    fun `splits a large subscription into batches`() {
        val core = RecordingCore()
        val probe = WarmLatencyProbe(core, LatencyRepository(), FixedTransport(10), batchSize = 4)

        runTest { probe.measureAll(nodes(10)) }

        assertEquals(3, core.calls.count { it == "start" })
    }

    @Test
    fun `falls back to one node at a time when the batch is rejected`() {
        // A merged config the core will not take must not cost the whole batch
        // its measurements.
        val core = object : RecordingCore() {
            override fun start(configJson: String): Result<Unit> {
                calls += "start"
                // Reject only the merged config, which carries several inbounds.
                if (configJson.contains("p1_in")) return Result.failure(XrayException("rejected"))
                running = true
                return Result.success(Unit)
            }
        }
        val latencies = LatencyRepository()

        runTest { WarmLatencyProbe(core, latencies, FixedTransport(33)).measureAll(nodes(3)) }

        (1..3).forEach { assertEquals(Latency.Value(33), latencies.latencyOf("n$it")) }
    }

    @Test
    fun `a connect interrupts a pass instead of queuing behind it`() = runTest {
        // The complaint this fixes: tapping connect during a pass did nothing for
        // seconds, because the tunnel waited out the node being measured.
        val core = RecordingCore()
        val reached = CompletableDeferred<Unit>()
        val transport = object : ProbeTransport {
            override suspend fun warmRoundTripMs(socksPort: Int): Long? {
                reached.complete(Unit)
                awaitCancellation()
            }
        }

        val pass = async { WarmLatencyProbe(core, LatencyRepository(), transport).measureAll(nodes(3)) }
        reached.await()
        XrayCoreOwner.claimForTunnel()

        val result = pass.await()
        assertTrue(result.isFailure)
        assertEquals(
            ProbeRefusal.TunnelActive,
            (result.exceptionOrNull() as ProbeRefusedException).reason,
        )
        assertFalse("the core must be released for the tunnel", core.isRunning())
    }

    @Test
    fun `a leaked instance does not prevent measurement`() {
        // A core running with nobody claiming it is a leak from an earlier failure.
        // Refusing to probe would leave it stuck that way, so the pass clears it.
        val core = RecordingCore()
        core.start("{}")
        assertTrue(core.isRunning())

        val latencies = LatencyRepository()
        runTest { WarmLatencyProbe(core, latencies, FixedTransport(15)).measureAll(nodes(1)) }

        assertEquals(Latency.Value(15), latencies.latencyOf("n1"))
    }

    @Test
    fun `stands down as soon as the tunnel claims the core`() {
        val core = RecordingCore()
        val latencies = LatencyRepository()
        val probe = WarmLatencyProbe(core, latencies, FixedTransport(20))

        XrayCoreOwner.claimForTunnel()
        runTest { assertTrue(probe.measureAll(nodes(5)).isFailure) }

        assertTrue(core.calls.isEmpty())
        assertFalse("must not be left spinning", latencies.measuring.value)
    }

    @Test
    fun `refuses a pass while a tunnel is already up`() {
        val core = RecordingCore(running = true)
        XrayCoreOwner.claimForTunnel()
        val probe = WarmLatencyProbe(core, LatencyRepository(), FixedTransport(20))

        runTest {
            val result = probe.measureAll(nodes(3))
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ProbeRefusedException)
        }
        // The live tunnel must be left completely alone.
        assertTrue(core.calls.isEmpty())
        assertTrue(core.isRunning())
    }

    @Test
    fun `an unreachable node is recorded as failed, not left blank`() {
        val latencies = LatencyRepository()
        val probe = WarmLatencyProbe(RecordingCore(), latencies, FixedTransport(null))

        runTest { probe.measureAll(nodes(2)) }

        assertTrue(latencies.latencyOf("n1") is Latency.Failed)
        assertFalse(latencies.measuring.value)
    }

    @Test
    fun `measuring the active node goes through the live proxy, not a new core`() {
        val core = RecordingCore(running = true)
        val latencies = LatencyRepository()
        val probe = WarmLatencyProbe(core, latencies, FixedTransport(31))

        runTest { assertEquals(Latency.Value(31), probe.measureActive("n1", 10808)) }

        // The running tunnel must not be touched.
        assertTrue(core.calls.isEmpty())
        assertTrue(core.isRunning())
    }

    @Test
    fun `an empty node list is refused rather than treated as success`() {
        val probe = WarmLatencyProbe(RecordingCore(), LatencyRepository(), FixedTransport(1))
        runTest { assertTrue(probe.measureAll(emptyList()).isFailure) }
    }
}
