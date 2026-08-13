package vpn.moonlight.core.xray

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Arbitrates the one thing there is only one of.
 *
 * libXray keeps a single global core per process — a second `runXrayFromJson`
 * fails with "xray is already running". Two callers want it: the tunnel, which
 * holds it for the whole session, and the latency probe, which takes it for a
 * second at a time per node. Without arbitration they collide, and the loser
 * reports a failure the user cannot act on.
 *
 * Rules:
 *  - every start/stop happens inside [exclusive], so they cannot interleave;
 *  - the tunnel announces itself with [claimForTunnel] before queuing, and the
 *    probe checks [isTunnelClaiming] between nodes and stands down — so a connect
 *    waits for at most the node being measured, not the whole pass.
 */
object XrayCoreOwner {

    private val lock = Mutex()
    private val tunnelClaiming = AtomicBoolean(false)

    /** True while a connect is starting or a tunnel is up. */
    val isTunnelClaiming: Boolean get() = tunnelClaiming.get()

    fun claimForTunnel() = tunnelClaiming.set(true)

    fun releaseTunnelClaim() = tunnelClaiming.set(false)

    /** Serialises access to the global core. */
    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock { block() }
}
