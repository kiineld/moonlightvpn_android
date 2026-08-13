package vpn.moonlight.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
    fun `marking idle clears a stuck measuring state`() {
        val repository = LatencyRepository()
        repository.markMeasuring(listOf("a"))
        assertEquals(Latency.Measuring, repository.latencyOf("a"))
        repository.markIdle()
        assertEquals(Latency.Unknown, repository.latencyOf("a"))
    }
}
