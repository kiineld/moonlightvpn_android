package vpn.moonlight.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow
import vpn.moonlight.core.tunnel.TunnelCounters
import vpn.moonlight.data.model.ConnectionState

/** The app's handle on the tunnel. Keeps `VpnService` plumbing out of the UI. */
object VpnController {

    val state: StateFlow<ConnectionState> get() = TunnelState.state
    val counters: StateFlow<TunnelCounters> get() = TunnelState.counters

    /** Port of the running core's SOCKS inbound, or null when the tunnel is down. */
    val socksPort: StateFlow<Int?> get() = TunnelState.socksPort

    /**
     * Returns the consent intent to launch, or null when permission is already
     * granted. Must be called on the main thread from an Activity context.
     */
    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)

    fun connect(context: Context) {
        val intent = Intent(context, MoonlightVpnService::class.java)
            .setAction(MoonlightVpnService.ACTION_CONNECT)
        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, MoonlightVpnService::class.java)
            .setAction(MoonlightVpnService.ACTION_DISCONNECT)
        // Not startForegroundService: the service is already foreground, and a
        // stop request must not be able to promote a dead service.
        runCatching { context.startService(intent) }
    }

    fun toggle(context: Context) {
        if (state.value.isActive) disconnect(context) else connect(context)
    }
}
