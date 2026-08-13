package vpn.moonlight.core.xray

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Measures round-trip time through a local SOCKS proxy.
 *
 * An interface so the probe's sequencing can be tested without a network.
 */
interface ProbeTransport {
    /**
     * Establishes a connection through the proxy — TCP, the proxy handshake and
     * TLS — and only then times a request on that established connection.
     * Returns null if the node is unreachable.
     */
    suspend fun warmRoundTripMs(socksPort: Int): Long?
}

/**
 * The real measurement.
 *
 * Deliberately not libXray's own `ping`: it builds its transport with
 * `DisableKeepAlives: true`, so every probe pays a fresh TCP and TLS/Reality
 * handshake and reports the sum. That number says more about handshake cost than
 * about the latency you experience once connected.
 *
 * Here the first request is thrown away to establish the connection, then two
 * timed requests reuse it and the lower is reported — the steady-state RTT.
 */
class OkHttpProbeTransport(
    private val url: String = DEFAULT_URL,
    private val timeoutSeconds: Long = 5,
    private val timedAttempts: Int = 2,
) : ProbeTransport {

    override suspend fun warmRoundTripMs(socksPort: Int): Long? {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            // One connection, held open long enough for the timed requests to reuse it.
            .connectionPool(ConnectionPool(1, 2, TimeUnit.MINUTES))
            .retryOnConnectionFailure(false)
            .build()

        val request = Request.Builder().url(url).head().build()

        // execute() blocks in a socket read, which thread interruption does not
        // unblock, so cancellation has to reach the call itself. Without this a
        // connect that interrupts a pass still waits out the probe timeout.
        val onCancel = coroutineContext.job.invokeOnCompletion {
            runCatching { client.dispatcher.cancelAll() }
        }

        return try {
            withContext(Dispatchers.IO) {
                // Warm-up: pays the handshakes, result discarded.
                client.newCall(request).execute().close()

                var best: Long? = null
                repeat(timedAttempts) {
                    val startedAt = System.nanoTime()
                    client.newCall(request).execute().close()
                    val elapsed = (System.nanoTime() - startedAt) / 1_000_000
                    if (best == null || elapsed < best!!) best = elapsed
                }
                best
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // Not a failed measurement: the pass is standing down for the tunnel,
            // and reporting "unreachable" here would mark a node we never measured.
            throw cancelled
        } catch (e: Exception) {
            null
        } finally {
            onCancel.dispose()
            runCatching {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }

    companion object {
        /** A 204 endpoint over TLS: smallest possible response, real handshake. */
        const val DEFAULT_URL = "https://cp.cloudflare.com/generate_204"
    }
}
