package vpn.moonlight.core

import vpn.moonlight.core.xray.XrayCore
import vpn.moonlight.data.local.SettingsStore
import vpn.moonlight.data.repository.LatencyRepository
import vpn.moonlight.data.repository.SubscriptionRepository

/** What [MoonlightVpnService] needs in order to bring a tunnel up. */
class TunnelDependencies(
    val xray: XrayCore,
    val subscriptions: SubscriptionRepository,
    val settings: SettingsStore,
    val latencies: LatencyRepository,
)

/**
 * Implemented by the `Application`, so the service resolves its dependencies from
 * the application object instead of reaching into a global container.
 */
interface TunnelDependenciesProvider {
    val tunnelDependencies: TunnelDependencies
}
