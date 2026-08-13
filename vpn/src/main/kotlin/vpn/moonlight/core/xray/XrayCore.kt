package vpn.moonlight.core.xray

/**
 * The Xray-core operations the app needs, behind an interface so the tunnel logic
 * can be reasoned about — and the config assembly tested — without loading a
 * 50 MB native library.
 */
interface XrayCore {

    /** The bundled core's version string, e.g. "26.7.28". */
    fun version(): String

    fun isRunning(): Boolean

    /**
     * Parses a share link into an Xray config using the core's own parser.
     * Returns the full config JSON, from which [XrayConfigBuilder] takes the outbound.
     */
    fun convertShareLink(shareLink: String): Result<String>

    fun start(configJson: String): Result<Unit>

    fun stop(): Result<Unit>

    /** Asks the core for [count] unused local ports. */
    fun freePorts(count: Int): Result<List<Int>>

    /**
     * Installs the callback the core uses to keep its own sockets outside the
     * tunnel. Without this, the core's connection to the node is itself routed
     * into the tunnel and the whole thing deadlocks.
     */
    fun registerSocketProtector(protect: (Int) -> Boolean)

    /** Points the core's Go resolver at a DNS server reachable outside the tunnel. */
    fun setDns(server: String, protect: (Int) -> Boolean): Result<Unit>

    fun resetDns()
}
