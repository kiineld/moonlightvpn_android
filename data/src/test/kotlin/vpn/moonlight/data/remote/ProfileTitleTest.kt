package vpn.moonlight.data.remote

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTitleTest {

    @Test
    fun `decodes the base64 prefix remnawave actually sends`() {
        // Regression: this arrived undecoded and was rendered as the plan name.
        val encoded = Base64.getEncoder().encodeToString("moonlight vpn 🌙".toByteArray())
        assertEquals("moonlight vpn 🌙", ProfileTitle.decode("base64:$encoded"))
    }

    @Test
    fun `decodes rfc 2047 encoded words`() {
        val encoded = Base64.getEncoder().encodeToString("Луна".toByteArray())
        assertEquals("Луна", ProfileTitle.decode("=?utf-8?B?$encoded?="))
    }

    @Test
    fun `leaves plain ascii titles alone`() {
        assertEquals("Luna", ProfileTitle.decode("Luna"))
        assertEquals("Luna", ProfileTitle.decode("  Luna  "))
    }

    @Test
    fun `an undecodable payload is shown verbatim rather than dropped`() {
        // Better to surface what the panel sent than to render nothing.
        assertEquals("base64:!!!!", ProfileTitle.decode("base64:!!!!"))
    }

    @Test
    fun `absent titles stay absent`() {
        assertNull(ProfileTitle.decode(null))
        assertNull(ProfileTitle.decode(""))
        assertNull(ProfileTitle.decode("   "))
    }
}
