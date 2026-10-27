package vpn.moonlight.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vpn.moonlight.core.tunnel.HevTunnelConfig
import vpn.moonlight.core.tunnel.Tun2Socks
import vpn.moonlight.core.xray.XrayAssets
import vpn.moonlight.core.xray.XrayCoreOwner
import vpn.moonlight.core.xray.XrayConfigBuilder
import vpn.moonlight.core.xray.XrayConfigOptions
import vpn.moonlight.core.xray.XrayLogFile
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.AppSettings
import vpn.moonlight.data.model.ConnectionError
import vpn.moonlight.data.model.ConnectionState
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.util.SplitTunnelPolicy
import vpn.moonlight.data.util.localizedFor
import vpn.moonlight.data.util.SplitTunnelRules

/**
 * Owns the tunnel.
 *
 * Bring-up order matters and is not interchangeable:
 *  1. register the socket protector, so the core's own sockets stay outside the
 *     tunnel — do this *before* starting the core, or its first dial can be
 *     captured by the tun and deadlock;
 *  2. start xray-core, which opens the local SOCKS inbound;
 *  3. establish the tun interface;
 *  4. hand the tun descriptor to hev-socks5-tunnel, which pumps packets into
 *     that SOCKS inbound.
 *
 * Teardown runs in reverse.
 */
class MoonlightVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val transition = Mutex()

    private var tunInterface: ParcelFileDescriptor? = null
    private var sessionJobs: Job? = null
    private var notifications: VpnNotifications? = null

    /**
     * Resources in the language chosen in the app.
     *
     * The service has its own context, which follows the *system* locale — so
     * without this the tunnel notification would be in a different language from
     * the app whenever the two disagree.
     */
    @Volatile
    private var strings: Context = this

    private val dependencies: TunnelDependencies?
        get() = (application as? TunnelDependenciesProvider)?.tunnelDependencies

    override fun onCreate() {
        super.onCreate()
        notifications = VpnNotifications(this).also {
            it.ensureChannel(
                getString(R.string.ml_notification_channel_name),
                getString(R.string.ml_notification_channel_description),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> scope.launch { tearDown(ConnectionState.Disconnected) }
            // A null intent is how the system starts an always-on VPN, so that
            // path must connect rather than be ignored.
            else -> {
                // Synchronously, before any of the slow work. The system allows
                // roughly five seconds between startForegroundService() and
                // startForeground(), then kills the process with
                // ForegroundServiceDidNotStartInTimeException. Bringing a tunnel
                // up involves extracting geodata, starting the core and
                // establishing the interface — easily longer than that — so the
                // notification cannot wait until the end.
                promoteToForeground(strings.getString(R.string.ml_notification_connecting))
                scope.launch { bringUp() }
            }
        }
        // Not sticky: the system restarting a VPN service without the user asking
        // would silently re-establish a tunnel they had stopped.
        return START_NOT_STICKY
    }

    /** The user revoked the VPN permission, or another VPN app took over. */
    override fun onRevoke() {
        scope.launch { tearDown(ConnectionState.Disconnected) }
    }

    override fun onDestroy() {
        scope.launch { tearDown(ConnectionState.Disconnected) }
        super.onDestroy()
    }

    private suspend fun bringUp() = transition.withLock {
        val deps = dependencies ?: return@withLock fail(ConnectionError.Unknown, "dependencies unavailable")

        TunnelState.update(ConnectionState.Connecting(nodeId = null))

        val settings = deps.settings.settings.first()
        strings = localizedFor(settings.language.tag)
        if (deps.subscriptions.subscription.value == null) deps.subscriptions.loadCached()
        // `Auto` picks by the last measured latency, which may still be on disk
        // if the service was started before the app got round to reading it.
        deps.latencies.restore()

        val subscription = deps.subscriptions.subscription.value
            ?: return@withLock fail(ConnectionError.NoSubscription)

        subscription.userInfo?.let { info ->
            val now = System.currentTimeMillis() / 1000
            if (info.isExpired(now)) return@withLock fail(ConnectionError.SubscriptionExpired)
        }

        val node = deps.subscriptions.resolve(settings.selection)
            ?: return@withLock fail(ConnectionError.NoNodes)

        TunnelState.update(ConnectionState.Connecting(node.id))

        // Step 1 — protection first. See the class comment.
        deps.xray.registerSocketProtector(::protectCoreSocket)
        deps.xray.setDns(DNS_PRIMARY_ENDPOINT, ::protectCoreSocket)

        val socksPort = deps.xray.freePorts(1).getOrNull()?.firstOrNull() ?: FALLBACK_SOCKS_PORT

        // Geodata has to be on disk before a config loads: every panel config
        // references geosite:/geoip: rules, and the core rejects what it cannot resolve.
        if (!XrayAssets.extract(this)) {
            return@withLock fail(ConnectionError.CoreStartFailed, "geodata unavailable")
        }

        // Step 2 — the core.
        // Fresh log per attempt: a support log should describe this connect, not
        // every connect since the app was installed.
        XrayLogFile.truncate(this)

        val options = XrayConfigOptions(
            socksPort = socksPort,
            assetDir = XrayAssets.directory(this).absolutePath,
            logFile = XrayLogFile.file(this).absolutePath,
            logLevel = if (settings.verboseLogging) "debug" else "warning",
            dnsOverride = settings.dnsServers,
        )
        MoonlightLog.i(
            TAG,
            "connecting to ${node.name} (${node.protocolLabel}) via socks:$socksPort",
        )
        val config = runCatching { buildConfig(deps, node, options) }
            .getOrElse {
                MoonlightLog.e(TAG, "could not build config for ${node.name}", it)
                return@withLock fail(ConnectionError.CoreStartFailed, it.message)
            }

        val started = XrayCoreOwner.exclusive {
            // Clears an instance left behind by an earlier failed attempt or by a
            // probe that was killed mid-measurement. Without this, one failure
            // poisons every later connect with "xray is already running".
            deps.xray.stop()
            deps.xray.start(config)
        }
        started.getOrElse {
            MoonlightLog.e(TAG, "core failed to start", it)
            return@withLock fail(ConnectionError.CoreStartFailed, it.message)
        }

        // Step 3 — the tun interface.
        val descriptor = establishTun(settings)
        if (descriptor == null) {
            XrayCoreOwner.exclusive { deps.xray.stop() }
            // establish() returns null when the user has not granted (or has
            // revoked) the VPN permission.
            return@withLock fail(ConnectionError.PermissionDenied)
        }
        tunInterface = descriptor

        // Step 4 — packets.
        val hevConfig = writeTunnelConfig(socksPort)
        val tunnelStarted = Tun2Socks.start(hevConfig.absolutePath, descriptor.fd)
        if (!tunnelStarted) {
            MoonlightLog.e(TAG, "tun2socks refused to start")
            closeTun()
            XrayCoreOwner.exclusive { deps.xray.stop() }
            return@withLock fail(ConnectionError.TunnelStartFailed)
        }

        MoonlightLog.i(TAG, "tunnel up on ${node.name}")
        // The panel's balancing node is called "Auto"; a Russian notification
        // should not read "Подключено к Auto".
        val shown = if (node.isBareAutoName) strings.getString(R.string.ml_node_auto) else node.name
        promoteToForeground(strings.getString(R.string.ml_notification_connected, shown))
        TunnelState.update(ConnectionState.Connected(node.id, System.currentTimeMillis()))
        startSessionJobs(verboseLogging = settings.verboseLogging)
    }

    /**
     * Keeps one of the core's sockets out of the tunnel.
     *
     * The core keeps this callback for the life of the process and fails any
     * dial it returns false for — including the latency probe's, long after the
     * session that registered it. So a refusal only counts while there is a
     * tunnel of ours to stay out of: with it down there is nothing to bypass,
     * and `protect` may well refuse, since the system honours it only for the
     * app currently holding the VPN.
     */
    private fun protectCoreSocket(fd: Int): Boolean =
        protect(fd) || !TunnelState.state.value.isActive

    private suspend fun tearDown(finalState: ConnectionState) = transition.withLock {
        MoonlightLog.i(TAG, "tearing the tunnel down")
        sessionJobs?.cancelAndJoin()
        sessionJobs = null

        Tun2Socks.stop()
        closeTun()
        dependencies?.xray?.let { core ->
            XrayCoreOwner.exclusive { core.stop() }
            core.resetDns()
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        TunnelState.update(finalState)
        stopSelf()
    }

    /**
     * A panel config is used verbatim apart from its inbound; a share link has to
     * be parsed by the core first and wrapped.
     */
    private fun buildConfig(
        deps: TunnelDependencies,
        node: ServerNode,
        options: XrayConfigOptions,
    ): String {
        node.panelConfigJson?.let { return XrayConfigBuilder.fromPanelConfig(it, options) }

        val link = node.shareLink
            ?: throw IllegalStateException("node ${node.id} has neither a config nor a link")
        val converted = deps.xray.convertShareLink(link).getOrThrow()
        return XrayConfigBuilder.build(converted, options)
    }

    private fun fail(reason: ConnectionError, detail: String? = null) {
        MoonlightLog.e(TAG, "tunnel failed: $reason ${detail.orEmpty()}")
        Tun2Socks.stop()
        closeTun()
        // The core has to be stopped here too. Leaving it running was what turned a
        // single failed attempt into every later attempt failing as "already running".
        dependencies?.xray?.let { core ->
            core.stop()
            core.resetDns()
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        TunnelState.update(ConnectionState.Error(reason, detail))
        stopSelf()
    }

    private fun closeTun() {
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun establishTun(settings: AppSettings): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.ml_notification_title))
            .setMtu(MTU)
            .addAddress(TUN_IPV4, TUN_IPV4_PREFIX)
            .addRoute("0.0.0.0", 0)
            // IPv6 is routed in as well. If it were left out, a device with
            // working IPv6 would send that traffic straight out the physical
            // interface and leak around the tunnel.
            .addAddress(TUN_IPV6, TUN_IPV6_PREFIX)
            .addRoute("::", 0)
            // Answered inside the core by the config's own DNS; see TUN_DNS.
            .addDnsServer(XrayConfigBuilder.TUN_DNS)
            .setBlocking(false)

        applySplitTunnel(builder, settings)

        launcherIntent()?.let { builder.setConfigureIntent(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        return runCatching { builder.establish() }
            .onFailure { MoonlightLog.e(TAG, "could not establish tun", it) }
            .getOrNull()
    }

    private fun applySplitTunnel(builder: Builder, settings: AppSettings) {
        val rules = SplitTunnelPolicy.rules(
            mode = settings.splitMode,
            selected = settings.splitPackages,
            selfPackage = packageName,
        )
        when (rules) {
            is SplitTunnelRules.Allow -> rules.packages.forEach { pkg ->
                // An app can be uninstalled between the user picking it and the
                // tunnel starting; skipping it beats failing the connection.
                runCatching { builder.addAllowedApplication(pkg) }
                    .onFailure { MoonlightLog.w(TAG, "allowed app is gone: $pkg") }
            }
            is SplitTunnelRules.Disallow -> rules.packages.forEach { pkg ->
                runCatching { builder.addDisallowedApplication(pkg) }
                    .onFailure { MoonlightLog.w(TAG, "disallowed app is gone: $pkg") }
            }
        }
    }

    private fun writeTunnelConfig(socksPort: Int): File {
        val file = File(cacheDir, TUNNEL_CONFIG_NAME)
        file.writeText(HevTunnelConfig.yaml(socksPort = socksPort, mtu = MTU))
        return file
    }

    /** Safe to call repeatedly: later calls just update the notification text. */
    private fun promoteToForeground(text: String) {
        val presenter = notifications ?: return
        val notification = presenter.build(
            title = strings.getString(R.string.ml_notification_title),
            text = text,
            smallIconRes = R.drawable.ml_ic_notification,
            contentIntent = launcherIntent(),
            disconnectLabel = strings.getString(R.string.ml_notification_disconnect),
            disconnectIntent = disconnectIntent(),
        )
        ServiceCompat.startForeground(
            this,
            VpnNotifications.NOTIFICATION_ID,
            notification,
            // Android 14+ requires a type. There is no VPN-specific one, so a
            // VPN client declares specialUse with a documented subtype.
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    /**
     * The work that runs for as long as the tunnel is up — which is to say for
     * hours, mostly with the screen off, so none of it may be unconditional.
     *
     * The traffic counters are polled only while something is showing them.
     * This used to be a wake-up every second for the whole session, to update a
     * number on a screen that was not on.
     *
     * The log is trimmed only at debug level, the one level at which the core
     * writes enough to need it; at the default it logs a few hundred bytes a
     * session and the file was being measured twice a minute for nothing.
     */
    private fun startSessionJobs(verboseLogging: Boolean) {
        sessionJobs = scope.launch {
            launch {
                TunnelState.countersWatched.collectLatest { watched ->
                    if (!watched) return@collectLatest
                    while (true) {
                        Tun2Socks.stats()?.let(TunnelState::updateCounters)
                        delay(STATS_INTERVAL_MS)
                    }
                }
            }
            if (verboseLogging) {
                launch {
                    while (true) {
                        delay(LOG_TRIM_INTERVAL_MS)
                        XrayLogFile.trimIfTooLarge(this@MoonlightVpnService)
                    }
                }
            }
        }
    }

    private fun launcherIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun disconnectIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, MoonlightVpnService::class.java).setAction(ACTION_DISCONNECT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "MoonlightVpn"

        const val ACTION_CONNECT = "vpn.moonlight.core.action.CONNECT"
        const val ACTION_DISCONNECT = "vpn.moonlight.core.action.DISCONNECT"

        /** Must match the MTU in the hev config, or large packets are dropped. */
        const val MTU = 8500

        private const val TUN_IPV4 = "198.18.0.1"
        private const val TUN_IPV4_PREFIX = 32
        private const val TUN_IPV6 = "fc00::1"
        private const val TUN_IPV6_PREFIX = 128

        private const val DNS_PRIMARY_ENDPOINT = "1.1.1.1:53"

        private const val FALLBACK_SOCKS_PORT = 10808
        private const val TUNNEL_CONFIG_NAME = "tun2socks.yml"
        private const val STATS_INTERVAL_MS = 1_000L
        private const val LOG_TRIM_INTERVAL_MS = 30_000L
    }
}
