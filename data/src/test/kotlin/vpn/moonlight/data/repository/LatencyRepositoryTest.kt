package vpn.moonlight.data.repository

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vpn.moonlight.data.local.LatencyStore
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode

class LatencyRepositoryTest {

    private fun node(id: String, name: String) =
        ServerNode(id = id, shareLink = "vless://$id@h:443#$name", name = name)

    @Test
    fun `auto prefers the panel's own auto node over the fastest one`() {
        val repository = LatencyRepository()
        val nodes = listOf(node("a", "Amsterdam"), node("b", "Auto"), node("c", "Helsinki"))
        repository.publish(mapOf("a" to Latency.Value(5), "c" to Latency.Value(1)))

        // Helsinki is fastest, but the panel publishes an Auto node — that wins.
        assertEquals("b", repository.autoPick(nodes)!!.id)
    }

    @Test
    fun `the russian spelling is recognised too`() {
        val repository = LatencyRepository()
        val nodes = listOf(node("a", "Амстердам"), node("b", "Авто · Европа"))
        assertEquals("b", repository.autoPick(nodes)!!.id)
    }

    @Test
    fun `without an auto node it falls back to the lowest latency`() {
        val repository = LatencyRepository()
        val nodes = listOf(node("a", "Amsterdam"), node("b", "Helsinki"))
        repository.publish(mapOf("a" to Latency.Value(90), "b" to Latency.Value(20)))
        assertEquals("b", repository.autoPick(nodes)!!.id)
    }

    @Test
    fun `with no measurements at all it falls back to the first node`() {
        val repository = LatencyRepository()
        val nodes = listOf(node("a", "Amsterdam"), node("b", "Helsinki"))
        assertEquals("a", repository.autoPick(nodes)!!.id)
    }

    @Test
    fun `failed probes do not count as fast`() {
        val repository = LatencyRepository()
        val nodes = listOf(node("a", "Amsterdam"), node("b", "Helsinki"))
        repository.publish(mapOf("a" to Latency.Failed("timeout"), "b" to Latency.Value(80)))
        assertEquals("b", repository.autoPick(nodes)!!.id)
    }

    @Test
    fun `an empty subscription resolves to nothing`() {
        assertNull(LatencyRepository().autoPick(emptyList()))
    }

    @Test
    fun `a node being measured again keeps its last measurement`() {
        val repository = LatencyRepository()
        repository.publish(mapOf("a" to Latency.Value(80)))

        repository.markMeasuring(listOf("a", "b"))

        assertEquals(setOf("a", "b"), repository.pending.value)
        assertEquals(Latency.Value(80), repository.latencyOf("a"))
        assertEquals(Latency.Unknown, repository.latencyOf("b"))
    }

    @Test
    fun `a result replaces the old measurement and settles that node only`() {
        val repository = LatencyRepository()
        repository.publish(mapOf("a" to Latency.Value(80), "b" to Latency.Value(120)))
        repository.markMeasuring(listOf("a", "b"))

        repository.publish(mapOf("a" to Latency.Value(95)))

        assertEquals(Latency.Value(95), repository.latencyOf("a"))
        assertEquals(setOf("b"), repository.pending.value)
    }

    @Test
    fun `a pass cut short leaves the nodes it did not reach as they were`() {
        val repository = LatencyRepository()
        repository.publish(mapOf("a" to Latency.Value(80)))
        repository.markMeasuring(listOf("a"))

        repository.markIdle()

        assertTrue(repository.pending.value.isEmpty())
        assertEquals(Latency.Value(80), repository.latencyOf("a"))
    }

    @Test
    fun `measurements come back after the process is gone`() = runTest {
        val file = File.createTempFile("latencies", ".json").also { it.deleteOnExit() }
        val first = LatencyRepository(LatencyStore(file))
        first.publish(mapOf("a" to Latency.Value(80), "b" to Latency.Failed("timeout")))
        first.persist()

        val second = LatencyRepository(LatencyStore(file))
        second.restore()

        assertEquals(Latency.Value(80), second.latencyOf("a"))
        // A node that did not answer is remembered as such, not as never checked.
        assertTrue(second.latencyOf("b") is Latency.Failed)
    }

    @Test
    fun `restoring does not overwrite what was measured since launch`() = runTest {
        val file = File.createTempFile("latencies", ".json").also { it.deleteOnExit() }
        LatencyRepository(LatencyStore(file)).apply {
            publish(mapOf("a" to Latency.Value(80)))
            persist()
        }

        val repository = LatencyRepository(LatencyStore(file))
        repository.publish(mapOf("a" to Latency.Value(60)))
        repository.restore()

        assertEquals(Latency.Value(60), repository.latencyOf("a"))
    }

    @Test
    fun `nodes the subscription no longer has are not kept on disk`() = runTest {
        val file = File.createTempFile("latencies", ".json").also { it.deleteOnExit() }
        val first = LatencyRepository(LatencyStore(file))
        first.publish(mapOf("a" to Latency.Value(80), "gone" to Latency.Value(40)))
        first.persist()
        first.retainOnly(listOf("a"))

        val second = LatencyRepository(LatencyStore(file))
        second.restore()

        assertEquals(Latency.Unknown, second.latencyOf("gone"))
        assertEquals(Latency.Value(80), second.latencyOf("a"))
    }

    @Test
    fun `clearing forgets the stored measurements too`() = runTest {
        val file = File.createTempFile("latencies", ".json").also { it.deleteOnExit() }
        val first = LatencyRepository(LatencyStore(file))
        first.publish(mapOf("a" to Latency.Value(80)))
        first.persist()
        first.clear()

        val second = LatencyRepository(LatencyStore(file))
        second.restore()

        assertEquals(Latency.Unknown, second.latencyOf("a"))
    }
}
