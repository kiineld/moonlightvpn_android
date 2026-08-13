package vpn.moonlight.core

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.ConnectionState

/**
 * The quick-settings tile, so the tunnel can be toggled from the notification
 * shade without opening the app.
 *
 * The tile cannot ask for VPN consent itself — `VpnService.prepare` needs an
 * Activity — so the first ever tap opens the app to collect it. Afterwards
 * `prepare` returns null and the tile toggles directly.
 */
class MoonlightTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var observer: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val active = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        observer = active.launch {
            VpnController.state.collectLatest { render(it) }
        }
    }

    override fun onStopListening() {
        observer?.cancel()
        observer = null
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        val consent = runCatching { VpnService.prepare(this) }.getOrNull()
        if (consent != null) {
            MoonlightLog.i(TAG, "tile tapped without VPN consent; opening the app")
            openApp()
            return
        }

        MoonlightLog.i(TAG, "tile toggled")
        VpnController.toggle(this)
    }

    private fun render(state: ConnectionState) {
        val tile = qsTile ?: return

        tile.state = when {
            state is ConnectionState.Connected -> Tile.STATE_ACTIVE
            state.isActive -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.ml_notification_title)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                is ConnectionState.Connected -> getString(R.string.ml_tile_connected)
                is ConnectionState.Connecting, is ConnectionState.Reconnecting ->
                    getString(R.string.ml_notification_connecting)
                is ConnectionState.Error -> getString(R.string.ml_tile_error)
                ConnectionState.Disconnected -> getString(R.string.ml_tile_disconnected)
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ml_ic_tile)
        tile.updateTile()
    }

    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From Android 14 the shade only collapses for a PendingIntent.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }

    private companion object {
        const val TAG = "MoonlightTile"
    }
}
