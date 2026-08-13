package vpn.moonlight.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two questions the UI asks about a node's name, deliberately separate.
 *
 * [ServerNode.isAutoNode] decides whether it behaves as the panel's balancing
 * node — used for the lightning glyph and the Auto selection. [ServerNode.isBareAutoName]
 * decides whether the app may replace the wording with its own localised label,
 * which it may only do when the panel's name says nothing else.
 */
class AutoNodeNameTest {

    private fun node(name: String) = ServerNode(id = "n", name = name)

    @Test
    fun `recognises the panel's auto node in either language`() {
        assertTrue(node("Auto").isAutoNode)
        assertTrue(node("Авто").isAutoNode)
        assertTrue(node("AUTO").isAutoNode)
    }

    @Test
    fun `a bare auto name may be replaced with the app's own label`() {
        assertTrue(node("Auto").isBareAutoName)
        assertTrue(node("Авто").isBareAutoName)
        assertTrue(node(" auto ").isBareAutoName)
    }

    @Test
    fun `a name carrying more than the marker is left to the panel`() {
        // "Авто · Европа" says which region balances; replacing it with "Авто"
        // would make two such nodes indistinguishable.
        assertTrue(node("Авто · Европа").isAutoNode)
        assertFalse(node("Авто · Европа").isBareAutoName)
        assertFalse(node("Auto EU").isBareAutoName)
    }

    @Test
    fun `an ordinary node is neither`() {
        assertFalse(node("Amsterdam").isAutoNode)
        assertFalse(node("Amsterdam").isBareAutoName)
    }

    @Test
    fun `the flag is not part of the name the panel parser produces`() {
        // PanelConfigParser splits "🇪🇺 Auto" into flag + name, which is what lets
        // the lightning glyph stand in for the flag without touching the name.
        assertTrue(node("Auto").isBareAutoName)
        assertFalse(node("🇪🇺 Auto").isBareAutoName)
    }
}
