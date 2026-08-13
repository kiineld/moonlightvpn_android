package vpn.moonlight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import vpn.moonlight.data.model.ThemeMode
import vpn.moonlight.deeplink.DeepLink
import vpn.moonlight.deeplink.DeepLinks
import vpn.moonlight.design.MoonlightTheme
import vpn.moonlight.ui.MoonlightApp

class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // The activity is singleTask, so a link arriving while it is already open
    // comes through onNewIntent rather than a fresh onCreate.
    private val deepLink = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as MoonlightApplication).container

        // Must precede super.onCreate. Swaps the splash theme for the app theme.
        installSplashScreen()

        // Belt and braces for the window this activity owns. The system-drawn
        // starting window is handled by the day/night background resource plus
        // the night mode published in MoonlightApplication.
        applyWindowBackground(container.themeStartupCache.theme)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        deepLink.value = DeepLinks.parse(intent?.dataString)

        // Theme is read here rather than inside the tree so the very first
        // composition already has the right palette and nothing flashes.
        val themeFlow = container.settingsStore.settings
            .map { it.theme }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, ThemeMode.Dark)

        setContent {
            val theme by themeFlow.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val link by deepLink.collectAsStateWithLifecycle()
            MoonlightTheme(darkTheme = dark) {
                MoonlightApp(
                    container = container,
                    deepLink = link,
                    onDeepLinkHandled = { deepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = DeepLinks.parse(intent.dataString)
    }

    private fun applyWindowBackground(theme: ThemeMode) {
        val dark = when (theme) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.System ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(
            ColorDrawable(if (dark) WINDOW_DARK else WINDOW_LIGHT),
        )
    }

    /**
     * Asked for at launch, not when connecting.
     *
     * Without it on Android 13+ the tunnel still runs, but its foreground-service
     * notification is silently withheld — so the user has no visible indication
     * that the VPN is up and no way to disconnect from the shade.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        // MoonlightDarkColors.bg and MoonlightLightColors.bg.
        const val WINDOW_DARK = 0xFF101828.toInt()
        const val WINDOW_LIGHT = 0xFFF2F3ED.toInt()
    }
}
