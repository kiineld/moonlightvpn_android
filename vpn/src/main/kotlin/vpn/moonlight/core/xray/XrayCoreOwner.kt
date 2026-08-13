package vpn.moonlight.core.xray

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Arbitrates the one thing there is only one of.
 *
 * libXray keeps a single global core per process — a second `runXrayFromJson`
 * fails with "xray is already running". Two callers want it: the tunnel, which
 * holds it for the whole session, and the latency probe, which takes it for a
 * second at a time.
 *
 * Rules:
 *  - every start/stop happens inside [exclusive], so they cannot interleave;
 *  - the tunnel announces itself with [claimForTunnel], which **interrupts** an
 *    in-flight measurement rather than queuing behind it.
 *
 * The interrupt matters. Checking a flag between nodes still makes a connect wait
 * out the node being measured, and an unreachable node takes the full probe
 * timeout — so tapping connect during a pass appeared to do nothing for seconds.
 * Cancelling the pass outright costs only the core stop.
 */
object XrayCoreOwner {

    private val lock = Mutex()
    private val tunnelClaiming = AtomicBoolean(false)
    private val standDown = AtomicReference<(() -> Unit)?>(null)

    /** True while a connect is starting or a tunnel is up. */
    val isTunnelClaiming: Boolean get() = tunnelClaiming.get()

    /**
     * Registers how to interrupt the current latency pass, or clears it with null.
     * Only one pass runs at a time, so a single slot is enough.
     */
    fun onTunnelClaim(handler: (() -> Unit)?) = standDown.set(handler)

    fun claimForTunnel() {
        tunnelClaiming.set(true)
        // Taken, not read, so a pass is interrupted exactly once.
        standDown.getAndSet(null)?.invoke()
    }

    fun releaseTunnelClaim() = tunnelClaiming.set(false)

    /** Serialises access to the global core. */
    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock { block() }
}
