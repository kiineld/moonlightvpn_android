package vpn.moonlight.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vpn.moonlight.AppContainer
import vpn.moonlight.R
import vpn.moonlight.core.VpnController
import vpn.moonlight.deeplink.DeepLink
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.components.MlTab
import vpn.moonlight.design.components.MlTabBar
import vpn.moonlight.design.ml
import vpn.moonlight.ui.connect.ConnectScreen
import vpn.moonlight.ui.connect.ConnectViewModel
import vpn.moonlight.ui.importsub.ImportScreen
import vpn.moonlight.ui.importsub.ImportViewModel
import vpn.moonlight.ui.settings.SettingsScreen
import vpn.moonlight.ui.settings.SettingsViewModel
import vpn.moonlight.ui.split.SplitTunnelScreen
import vpn.moonlight.ui.split.SplitTunnelViewModel
import vpn.moonlight.ui.subscription.SubscriptionScreen
import vpn.moonlight.ui.subscription.SubscriptionViewModel

/**
 * The whole app shell.
 *
 * Navigation is a single `AnimatedContent` over [Destination] rather than a
 * NavHost: there are six flat screens, no deep links and no arguments, so a
 * navigation graph would add a dependency and a back-stack model without
 * removing any of this code.
 */
@Composable
fun MoonlightApp(
    container: AppContainer,
    deepLink: DeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    // The app opens on the connect screen. With no subscription yet, that
    // screen is itself the way to add one, so there is no welcome to sit
    // through first.
    var currentRoute by rememberSaveable { mutableStateOf(Destination.Connect.route) }

    // A route saved by a version that still had other screens falls back here.
    val current: Destination = Destination.fromRoute(currentRoute) ?: Destination.Connect

    // Without this, the system back gesture falls through to the activity and
    // closes the app from a pushed screen. Handled here rather than per screen so
    // the whole back graph is visible in one place.
    val backTarget: Destination? = when (current) {
        Destination.SplitTunnel, Destination.Logs -> Destination.Settings
        Destination.Import -> Destination.Subscription
        // From a secondary tab, back returns to the first tab before exiting.
        Destination.Subscription, Destination.Settings -> Destination.Connect
        Destination.Connect -> null
    }
    BackHandler(enabled = backTarget != null) {
        backTarget?.let { currentRoute = it.route }
    }

    // Carried as state rather than acted on directly, because the screens own the
    // consent launcher and the import view model.
    var pendingImportUrl by remember { mutableStateOf<String?>(null) }
    var pendingConnect by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(deepLink) {
        when (val link = deepLink) {
            null -> return@LaunchedEffect
            is DeepLink.ImportSubscription -> {
                pendingImportUrl = link.url
                currentRoute = Destination.Import.route
            }
            DeepLink.Connect -> {
                pendingConnect = true
                currentRoute = Destination.Connect.route
            }
            DeepLink.Disconnect -> {
                VpnController.disconnect(context)
                currentRoute = Destination.Connect.route
            }
            DeepLink.Open -> currentRoute = Destination.Connect.route
        }
        onDeepLinkHandled()
    }

    val connectViewModel: ConnectViewModel = viewModel(factory = ConnectViewModel.factory(container))
    val subscriptionViewModel: SubscriptionViewModel =
        viewModel(factory = SubscriptionViewModel.factory(container))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))

    Box(
        Modifier
            .fillMaxSize()
            .background(ml.colors.bg),
    ) {
        AnimatedContent(
            targetState = current,
            // How every page arrives: it fades in as it settles the last few
            // points into place, on the one curve. The page it replaces just
            // goes — nobody watches a page leave.
            transitionSpec = {
                val rise = with(density) { MlMotion.RiseDistance.roundToPx() }
                (
                    fadeIn(MlMotion.enter()) +
                        slideInVertically(MlMotion.enter(IntOffset.VisibilityThreshold)) { rise }
                    ) togetherWith fadeOut(MlMotion.paint())
            },
            label = "screen",
        ) { destination ->
            when (destination) {
                Destination.Import -> {
                    val importViewModel: ImportViewModel =
                        viewModel(factory = ImportViewModel.factory(container))
                    ImportScreen(
                        viewModel = importViewModel,
                        onBack = { currentRoute = Destination.Subscription.route },
                        onDone = { currentRoute = Destination.Connect.route },
                        initialUrl = pendingImportUrl,
                        onInitialUrlConsumed = { pendingImportUrl = null },
                    )
                }

                Destination.Connect -> ConnectScreen(
                    viewModel = connectViewModel,
                    onAddSubscription = { currentRoute = Destination.Import.route },
                    autoConnect = pendingConnect,
                    onAutoConnectConsumed = { pendingConnect = false },
                )

                Destination.Subscription -> SubscriptionScreen(
                    viewModel = subscriptionViewModel,
                    onAddSubscription = { currentRoute = Destination.Import.route },
                )

                Destination.Settings -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenSplitTunnel = { currentRoute = Destination.SplitTunnel.route },
                    onOpenLogs = { currentRoute = Destination.Logs.route },
                )

                Destination.Logs -> {
                    val logsViewModel: vpn.moonlight.ui.logs.LogsViewModel =
                        viewModel(factory = vpn.moonlight.ui.logs.LogsViewModel.factory(container))
                    vpn.moonlight.ui.logs.LogsScreen(
                        viewModel = logsViewModel,
                        onBack = { currentRoute = Destination.Settings.route },
                    )
                }

                Destination.SplitTunnel -> {
                    val splitViewModel: SplitTunnelViewModel =
                        viewModel(factory = SplitTunnelViewModel.factory(container))
                    SplitTunnelScreen(
                        viewModel = splitViewModel,
                        onBack = { currentRoute = Destination.Settings.route },
                    )
                }
            }
        }

        if (current in Destination.tabs) {
            val tabs = remember {
                listOf(
                    MlIcons.Power to R.string.nav_connect,
                    MlIcons.Sparkles to R.string.nav_subscription,
                    MlIcons.Settings to R.string.nav_settings,
                )
            }
            MlTabBar(
                tabs = tabs.map { (icon, labelRes) -> MlTab(icon, stringResource(labelRes)) },
                selectedIndex = Destination.tabs.indexOf(current),
                onSelect = { index -> currentRoute = Destination.tabs[index].route },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
            )
        }
    }
}
