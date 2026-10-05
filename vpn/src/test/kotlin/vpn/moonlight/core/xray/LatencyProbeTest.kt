package vpn.moonlight.core.xray

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vpn.moonlight.data.local.LatencyStore
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.repository.LatencyRepository

class LatencyProbeTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A core whose probe answers from a table, and records what it was asked. */
    private open class FakeCore(
        private val delays: (PingTarget, String) -> Int? = { _, _ -> 42 },
    ) : XrayCore {
        val batches = CopyOnWriteArrayList<List<PingTarget>>()
        val configsSeen = CopyOnWriteArrayList<String>()
        val starts = AtomicInteger()

        override fun version() = "26.7.28"
        override fun isRunning() = false

        override fun convertShareLink(shareLink: String) =
            Result.success("""{"outbounds":[{"tag":"x","protocol":"vless","sendThrough":"name"}]}""")

        override fun start(configJson: String): Result<Unit> {
            starts.incrementAndGet()
            return Result.success(Unit)
        }

        override fun stop() = Result.success(Unit)
        override fun freePorts(count: Int) = Result.success(List(count) { 10810 + it })

        override fun pingBatch(
            targets: List<PingTarget>,
            timeoutSeconds: Int,
            url: String,
        ): Result<List<Int?>> {
            batches += targets
            return Result.success(
                targets.map { target ->
                    val config = File(target.configPath).readText()
                    configsSeen += config
                    delays(target, config)
                },
            )
        }

        override fun registerSocketProtector(protect: (Int) -> Boolean) = Unit
        override fun setDns(server: String, protect: (Int) -> Boolean) = Result.success(Unit)
        override fun resetDns() = Unit
    }

    private fun linkNodes(count: Int) = (1..count).map {
        ServerNode(id = "n$it", name = "N$it", shareLink = "vless://n$it@h:443#N$it")
    }

    private fun panelNode(id: String, config: String) =
        ServerNode(id = id, name = id, panelConfigJson = config)

    private fun probe(core: XrayCore, latencies: LatencyRepository, concurrency: Int = 8) =
        LatencyProbe(core, latencies, File(temp.root, "ping"), concurrency = concurrency)

    @Test
    fun `every node gets a measurement`() = runTest {
        val latencies = LatencyRepository()

        val result = probe(FakeCore(), latencies).measureAll(linkNodes(12))

        assertTrue(result.isSuccess)
        (1..12).forEach { assertEquals(Latency.Value(42), latencies.latencyOf("n$it")) }
        assertTrue(latencies.pending.value.isEmpty())
    }

    @Test
    fun `it never touches the instance that carries the tunnel`() = runTest {
        // The regression this replaces: the probe borrowed the one global core,
        // so with the tunnel up it could measure only the node in use.
        val core = FakeCore()

        probe(core, LatencyRepository()).measureAll(linkNodes(6))

        assertEquals(0, core.starts.get())
    }

    @Test
    fun `a node that does not answer is failed, and does not fail the others`() = runTest {
        val latencies = LatencyRepository()
        val core = FakeCore { _, config -> if (config.contains("dead")) null else 30 }
        val nodes = listOf(
            panelNode("a", """{"outbounds":[{"tag":"proxy","protocol":"vless","id":"alive"}]}"""),
            panelNode("b", """{"outbounds":[{"tag":"proxy","protocol":"vless","id":"dead"}]}"""),
        )

        val result = probe(core, latencies).measureAll(nodes)

        assertTrue(result.isSuccess)
        assertEquals(Latency.Value(30), latencies.latencyOf("a"))
        assertTrue(latencies.latencyOf("b") is Latency.Failed)
    }

    @Test
    fun `a balancer node is as fast as its fastest member`() = runTest {
        val latencies = LatencyRepository()
        val core = FakeCore { target, _ ->
            when (target.outboundTag) {
                "proxy-1" -> 180
                "proxy-2" -> null
                "proxy-3" -> 95
                else -> error("asked for ${target.outboundTag}")
            }
        }
        val balancer = """
            {"routing":{"balancers":[{"tag":"b","selector":["proxy"]}]},
             "outbounds":[{"tag":"proxy-1","protocol":"vless"},{"tag":"proxy-2","protocol":"vless"},
                          {"tag":"proxy-3","protocol":"vless"},{"tag":"direct","protocol":"freedom"}]}
        """.trimIndent()

        probe(core, latencies).measureAll(listOf(panelNode("lte", balancer)))

        assertEquals(Latency.Value(95), latencies.latencyOf("lte"))
        // One call for the node, its members side by side.
        assertEquals(listOf(listOf("proxy-1", "proxy-2", "proxy-3")), core.batches.map { it.map(PingTarget::outboundTag) })
    }

    @Test
    fun `a node whose config cannot be used is failed rather than left spinning`() = runTest {
        val latencies = LatencyRepository()
        val nodes = listOf(panelNode("bad", "not json")) + linkNodes(1)

        val result = probe(FakeCore(), latencies).measureAll(nodes)

        assertTrue(result.isSuccess)
        assertTrue(latencies.latencyOf("bad") is Latency.Failed)
        assertEquals(Latency.Value(42), latencies.latencyOf("n1"))
        assertTrue(latencies.pending.value.isEmpty())
    }

    @Test
    fun `the core failing a probe outright fails that node only`() = runTest {
        val latencies = LatencyRepository()
        val core = object : FakeCore() {
            override fun pingBatch(targets: List<PingTarget>, timeoutSeconds: Int, url: String) =
                if (File(targets.first().configPath).readText().contains("n1@")) {
                    Result.failure(XrayException("failed to build ping batch config"))
                } else {
                    super.pingBatch(targets, timeoutSeconds, url)
                }

            override fun convertShareLink(shareLink: String) =
                Result.success("""{"outbounds":[{"tag":"x","protocol":"vless","user":"$shareLink"}]}""")
        }

        probe(core, latencies).measureAll(linkNodes(2))

        assertTrue(latencies.latencyOf("n1") is Latency.Failed)
        assertEquals(Latency.Value(42), latencies.latencyOf("n2"))
    }

    @Test
    fun `the previous measurement stays on screen until the new one lands`() = runTest {
        val latencies = LatencyRepository()
        latencies.publish(mapOf("n1" to Latency.Value(70)))

        val asked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val core = FakeCore { _, _ ->
            asked.countDown()
            release.await(5, TimeUnit.SECONDS)
            55
        }

        // On real threads: the test thread is about to block on the latch.
        val pass = async(Dispatchers.Default) { probe(core, latencies).measureAll(linkNodes(1)) }
        assertTrue(asked.await(5, TimeUnit.SECONDS))

        // Out for a new number, still showing the old one.
        assertEquals(setOf("n1"), latencies.pending.value)
        assertEquals(Latency.Value(70), latencies.latencyOf("n1"))

        release.countDown()
        pass.await()
        assertEquals(Latency.Value(55), latencies.latencyOf("n1"))
    }

    @Test
    fun `no more nodes are measured at once than the limit`() = runTest {
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        val core = FakeCore { _, _ ->
            peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            Thread.sleep(20)
            inFlight.decrementAndGet()
            10
        }

        probe(core, LatencyRepository(), concurrency = 3).measureAll(linkNodes(12))

        assertTrue("peaked at ${peak.get()}", peak.get() <= 3)
    }

    @Test
    fun `the remark the core leaves in a share link's outbound never reaches the probe`() = runTest {
        val core = FakeCore()

        probe(core, LatencyRepository()).measureAll(linkNodes(1))

        assertFalse(core.configsSeen.single().contains("sendThrough"))
    }

    @Test
    fun `a node's credentials do not stay on disk after the pass`() = runTest {
        probe(FakeCore(), LatencyRepository()).measureAll(linkNodes(4))

        assertTrue(File(temp.root, "ping").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `the measurements are written to disk when the pass ends`() = runTest {
        val file = File(temp.root, "latencies.json")
        probe(FakeCore(), LatencyRepository(LatencyStore(file))).measureAll(linkNodes(2))

        val reopened = LatencyRepository(LatencyStore(file))
        reopened.restore()
        assertEquals(Latency.Value(42), reopened.latencyOf("n1"))
        assertEquals(Latency.Value(42), reopened.latencyOf("n2"))
    }

    @Test
    fun `an empty subscription is refused without touching the core`() = runTest {
        val core = FakeCore()

        val result = probe(core, LatencyRepository()).measureAll(emptyList())

        assertTrue(result.isFailure)
        assertTrue(core.batches.isEmpty())
    }

    @Test
    fun `a pass in which nothing answers reports failure`() = runTest {
        val latencies = LatencyRepository()

        val result = probe(FakeCore { _, _ -> null }, latencies).measureAll(linkNodes(3))

        assertTrue(result.isFailure)
        assertTrue(latencies.latencyOf("n1") is Latency.Failed)
    }
}
