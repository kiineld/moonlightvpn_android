package vpn.moonlight.data.util

import vpn.moonlight.data.model.SplitMode

/**
 * Turns the user's split-tunnel choice into the two package sets that
 * `VpnService.Builder` understands.
 *
 * A builder may be given an allow list or a deny list, never both, so this is a
 * sealed choice rather than a pair of sets. There is no "no rules" case: every
 * mode excludes this app itself, so the result is always one list or the other.
 */
sealed interface SplitTunnelRules {
    /** Only these packages enter the tunnel. */
    data class Allow(val packages: Set<String>) : SplitTunnelRules

    /** These packages bypass the tunnel. */
    data class Disallow(val packages: Set<String>) : SplitTunnelRules
}

object SplitTunnelPolicy {

    /**
     * @param selfPackage this app's own package. It never goes through the
     *  tunnel: the app has to reach the panel to refresh a subscription even
     *  while connected, and routing it through its own tunnel risks a loop.
     */
    fun rules(
        mode: SplitMode,
        selected: Set<String>,
        selfPackage: String,
    ): SplitTunnelRules {
        val chosen = selected.filter { it.isNotBlank() && it != selfPackage }.toSet()

        return when (mode) {
            SplitMode.All -> SplitTunnelRules.Disallow(setOf(selfPackage))

            // An empty allow list would tunnel nothing at all, which reads as a
            // broken VPN rather than a configuration choice. Fall back to
            // tunnelling everything until the user picks an app.
            SplitMode.OnlySelected ->
                if (chosen.isEmpty()) {
                    SplitTunnelRules.Disallow(setOf(selfPackage))
                } else {
                    SplitTunnelRules.Allow(chosen)
                }

            SplitMode.ExceptSelected -> SplitTunnelRules.Disallow(chosen + selfPackage)
        }
    }
}
