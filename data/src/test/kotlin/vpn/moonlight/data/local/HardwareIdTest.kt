package vpn.moonlight.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareIdTest {

    @Test
    fun `the same device always gets the same hwid`() {
        assertEquals(HardwareId.from("a1b2c3d4e5f60718"), HardwareId.from("a1b2c3d4e5f60718"))
    }

    @Test
    fun `case and stray whitespace do not make a second device`() {
        assertEquals(HardwareId.from("a1b2c3d4e5f60718"), HardwareId.from(" A1B2C3D4E5F60718\n"))
    }

    @Test
    fun `different devices get different hwids`() {
        assertNotEquals(HardwareId.from("a1b2c3d4e5f60718"), HardwareId.from("a1b2c3d4e5f60719"))
    }

    @Test
    fun `it is laid out as a uuid and does not contain the platform id`() {
        val hwid = HardwareId.from("a1b2c3d4e5f60718")
        assertTrue(hwid, Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}").matches(hwid))
        assertFalse(hwid.replace("-", "").contains("A1B2C3D4E5F60718"))
    }

    @Test
    fun `an identifier that names no device is refused`() {
        assertFalse(HardwareId.isUsable(null))
        assertFalse(HardwareId.isUsable("  "))
        assertFalse(HardwareId.isUsable("0000000000000000"))
        assertFalse(HardwareId.isUsable("9774d56d682e549c"))
        assertTrue(HardwareId.isUsable("a1b2c3d4e5f60718"))
    }
}
