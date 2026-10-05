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
     * Times one request to [url] through each of [targets], all at once.
     *
     * The core does this in an instance of its own, built from the outbounds
     * alone — no inbounds, no routing, no geodata — so it neither needs nor
     * disturbs the instance carrying the tunnel. A pass runs the same whether
     * the tunnel is up or down.
     *
     * Each time is a fresh connection: the handshake with the node and the
     * request through it, which is what choosing that node would cost. The
     * result has one entry per target, in order; null is a node that did not
     * answer within [timeoutSeconds].
     *
     * @param targets at most [MAX_PING_BATCH]; the core refuses a larger batch.
     */
    fun pingBatch(targets: List<PingTarget>, timeoutSeconds: Int, url: String): Result<List<Int?>>

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

/**
 * One outbound to time: [configPath] is a file holding `{"outbounds": […]}`, and
 * [outboundTag] names the one in it to dial through.
 */
data class PingTarget(val configPath: String, val outboundTag: String)

/** The most targets the core accepts in one [XrayCore.pingBatch]. */
const val MAX_PING_BATCH = 5
