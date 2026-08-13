package vpn.moonlight.core.tunnel

/**
 * hev-socks5-tunnel's JNI surface.
 *
 * The native library registers these four methods against this exact class name
 * at `JNI_OnLoad`, using the `PKGNAME`/`CLSNAME` values baked in by
 * `scripts/build-tun2socks.sh`. Moving or renaming this class without changing
 * that script breaks the binding at load time, not compile time.
 *
 * Names deliberately keep hev's `TProxy*` spelling so they match the registered
 * natives; [start], [stop] and the rest are the Kotlin-facing wrappers.
 */
object Tun2Socks {

    @Volatile
    private var libraryLoaded = false

    @Synchronized
    private fun ensureLoaded(): Boolean {
        if (libraryLoaded) return true
        libraryLoaded = runCatching { System.loadLibrary("hev-socks5-tunnel") }.isSuccess
        return libraryLoaded
    }

    /**
     * @param tunFd the descriptor from `VpnService.Builder.establish()`. hev dups
     *  it internally; the caller keeps ownership.
     */
    fun start(configPath: String, tunFd: Int): Boolean {
        if (!ensureLoaded()) return false
        return runCatching { TProxyStartService(configPath, tunFd) }.getOrDefault(false)
    }

    fun stop(): Boolean {
        if (!libraryLoaded) return true
        return runCatching { TProxyStopService() }.getOrDefault(false)
    }

    fun isRunning(): Boolean = libraryLoaded && runCatching { TProxyIsRunning() }.getOrDefault(false)

    /** Cumulative counters since the tunnel started, or null if it is not running. */
    fun stats(): TunnelCounters? {
        if (!libraryLoaded) return null
        val raw = runCatching { TProxyGetStats() }.getOrNull() ?: return null
        if (raw.size < 4) return null
        return TunnelCounters(
            txPackets = raw[0],
            txBytes = raw[1],
            rxPackets = raw[2],
            rxBytes = raw[3],
        )
    }

    private external fun TProxyStartService(configPath: String, fd: Int): Boolean
    private external fun TProxyStopService(): Boolean
    private external fun TProxyIsRunning(): Boolean
    private external fun TProxyGetStats(): LongArray
}

/** Byte and packet counters straight off the tunnel. */
data class TunnelCounters(
    val txPackets: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val rxBytes: Long,
) {
    val totalBytes: Long get() = txBytes + rxBytes
}
