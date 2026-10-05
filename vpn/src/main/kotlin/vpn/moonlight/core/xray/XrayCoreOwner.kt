package vpn.moonlight.core.xray

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises starts and stops of the one thing there is only one of.
 *
 * libXray keeps a single global core per process — a second `runXrayFromJson`
 * fails with "xray is already running" — and the tunnel holds it for the whole
 * session. Every start and stop happens inside [exclusive], so a teardown and
 * the connect that follows it cannot interleave.
 *
 * The latency probe used to be a second claimant, taking this core between
 * sessions and having to be interrupted when a connect wanted it. It now runs in
 * an instance of its own ([XrayCore.pingBatch]) and never comes here.
 */
object XrayCoreOwner {

    private val lock = Mutex()

    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock { block() }
}
