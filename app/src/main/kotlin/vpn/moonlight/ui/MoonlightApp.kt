package vpn.moonlight.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vpn.moonlight.AppContainer
import vpn.moonlight.R
import vpn.moonlight.core.VpnController
import vpn.moonlight.deeplink.DeepLink
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.components.MlFab
import vpn.moonlight.design.components.MlPage
import vpn.moonlight.design.components.MlSplash
import vpn.moonlight.design.components.MlTab
import vpn.moonlight.design.components.MlTabBar
import vpn.moonlight.ui.auth.AuthScreen
import vpn.moonlight.ui.auth.AuthViewModel
import vpn.moonlight.ui.connect.ConnectScreen
import vpn.moonlight.ui.connect.ConnectViewModel
import vpn.moonlight.ui.home.HomeScreen
import vpn.moonlight.ui.home.HomeViewModel
import vpn.moonlight.ui.logs.LogsScreen
import vpn.moonlight.ui.logs.LogsViewModel
import vpn.moonlight.ui.plans.PlansScreen
import vpn.moonlight.ui.plans.PlansViewModel
import vpn.moonlight.ui.settings.SettingsScreen
import vpn.moonlight.ui.settings.SettingsViewModel
import vpn.moonlight.ui.split.SplitTunnelScreen
import vpn.moonlight.ui.split.SplitTunnelViewModel
import vpn.moonlight.ui.wallet.WalletScreen
import vpn.moonlight.ui.wallet.WalletViewModel

/**
 * The whole app: the cabinet's splash while the session is read, sign-in until
 * there is an account, then four tabs and the round plus that buys a plan.
 */
@Composable
fun MoonlightApp(
    container: AppContainer,
    language: String,
    deepLink: DeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val signedIn by container.accountRepository.signedIn.collectAsStateWithLifecycle()
    var splash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { container.accountRepository.restore() }

    MlPage {
        when (signedIn) {
            true -> SignedIn(container, deepLink, onDeepLinkHandled)
            false -> AuthScreen(viewModel(factory = AuthViewModel.factory(container)), language)
            null -> Unit
        }
        if (splash) MlSplash(ready = signedIn != null, onGone = { splash = false })
    }
}

@Composable
private fun SignedIn(container: AppContainer, deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    var currentRoute by rememberSaveable { mutableStateOf(Destination.Connect.route) }
    // Set when Plans is opened to renew rather than to buy.
    var renewId by rememberSaveable { mutableStateOf<Long?>(null) }
    val current: Destination = Destination.fromRoute(currentRoute) ?: Destination.Connect
    fun go(destination: Destination) { currentRoute = destination.route }

    // Without this, the system back gesture falls through to the activity and
    // closes the app from a pushed screen.
    val backTarget: Destination? = when (current) {
        Destination.SplitTunnel, Destination.Logs -> Destination.Settings
        Destination.Plans -> Destination.Home
        Destination.Home, Destination.Wallet, Destination.Settings -> Destination.Connect
        Destination.Connect -> null
    }
    BackHandler(enabled = backTarget != null) { backTarget?.let(::go) }

    var pendingConnect by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(deepLink) {
        when (deepLink) {
            null -> return@LaunchedEffect
            DeepLink.Connect -> {
                pendingConnect = true
                go(Destination.Connect)
            }
            DeepLink.Disconnect -> {
                VpnController.disconnect(context)
                go(Destination.Connect)
            }
            DeepLink.Open -> go(Destination.Connect)
        }
        onDeepLinkHandled()
    }

    val connectViewModel: ConnectViewModel = viewModel(factory = ConnectViewModel.factory(container))
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val walletViewModel: WalletViewModel = viewModel(factory = WalletViewModel.factory(container))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val openPlans = { id: Long? ->
        renewId = id
        go(Destination.Plans)
    }

    Box {
        AnimatedContent(
            targetState = current,
            // A page fades in as it settles the last few points into place; the
            // page it replaces just goes — nobody watches a page leave.
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
                Destination.Connect -> ConnectScreen(
                    viewModel = connectViewModel,
                    onAddSubscription = { go(Destination.Home) },
                    autoConnect = pendingConnect,
                    onAutoConnectConsumed = { pendingConnect = false },
                )
                Destination.Home -> HomeScreen(
                    viewModel = homeViewModel,
                    onBuy = { openPlans(null) },
                    onRenew = { openPlans(it.id) },
                )
                Destination.Wallet -> WalletScreen(walletViewModel)
                Destination.Settings -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenSplitTunnel = { go(Destination.SplitTunnel) },
                    onOpenLogs = { go(Destination.Logs) },
                )
                Destination.Plans -> PlansScreen(
                    // Keyed so buying and renewing never share a half-filled form.
                    viewModel = viewModel(key = "plans-$renewId", factory = PlansViewModel.factory(container, renewId)),
                    onBack = { go(Destination.Home) },
                    onDone = { go(Destination.Home) },
                )
                Destination.Logs -> LogsScreen(
                    viewModel = viewModel(factory = LogsViewModel.factory(container)),
                    onBack = { go(Destination.Settings) },
                )
                Destination.SplitTunnel -> SplitTunnelScreen(
                    viewModel = viewModel(factory = SplitTunnelViewModel.factory(container)),
                    onBack = { go(Destination.Settings) },
                )
            }
        }

        if (current in Destination.tabs) {
            val tabs = listOf(
                MlTab(MlIcons.Power, stringResource(R.string.nav_connect)),
                MlTab(MlIcons.Home, stringResource(R.string.nav_home)),
                MlTab(MlIcons.Wallet, stringResource(R.string.nav_wallet)),
                MlTab(MlIcons.Settings, stringResource(R.string.nav_settings)),
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MlTabBar(
                    tabs = tabs,
                    selectedIndex = Destination.tabs.indexOf(current),
                    onSelect = { index -> go(Destination.tabs[index]) },
                )
                MlFab(onClick = { openPlans(null) }, label = stringResource(R.string.nav_buy))
            }
        }
    }
}
