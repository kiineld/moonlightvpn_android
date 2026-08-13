package vpn.moonlight.data.util

import org.junit.Assert.assertEquals
import org.junit.Test
import vpn.moonlight.data.model.SplitMode

class SplitTunnelPolicyTest {

    private val self = "vpn.moonlight"

    @Test
    fun `all traffic still excludes the app itself`() {
        val rules = SplitTunnelPolicy.rules(SplitMode.All, setOf("a", "b"), self)
        assertEquals(SplitTunnelRules.Disallow(setOf(self)), rules)
    }

    @Test
    fun `only-selected allows exactly the chosen packages`() {
        val rules = SplitTunnelPolicy.rules(
            SplitMode.OnlySelected,
            setOf("org.telegram.messenger", "com.google.android.youtube"),
            self,
        )
        assertEquals(
            SplitTunnelRules.Allow(setOf("org.telegram.messenger", "com.google.android.youtube")),
            rules,
        )
    }

    @Test
    fun `except-selected disallows the chosen packages plus ourselves`() {
        val rules = SplitTunnelPolicy.rules(SplitMode.ExceptSelected, setOf("ru.bank.mobile"), self)
        assertEquals(SplitTunnelRules.Disallow(setOf("ru.bank.mobile", self)), rules)
    }

    @Test
    fun `an empty only-selected list tunnels everything instead of nothing`() {
        // An empty allow list would route no traffic at all, which a user reads
        // as a broken VPN rather than as their own configuration.
        val rules = SplitTunnelPolicy.rules(SplitMode.OnlySelected, emptySet(), self)
        assertEquals(SplitTunnelRules.Disallow(setOf(self)), rules)
    }

    @Test
    fun `our own package cannot be added to an allow list`() {
        val rules = SplitTunnelPolicy.rules(SplitMode.OnlySelected, setOf(self, "other"), self)
        assertEquals(SplitTunnelRules.Allow(setOf("other")), rules)
    }

    @Test
    fun `blank package names are ignored`() {
        val rules = SplitTunnelPolicy.rules(SplitMode.OnlySelected, setOf("", "  ", "real"), self)
        assertEquals(SplitTunnelRules.Allow(setOf("real")), rules)
    }
}
