package vpn.moonlight.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TunnelStateTest {

    @Test
    fun `the counters are watched only while something is collecting them`() = runTest {
        // What the service polls the tunnel on. With the connect screen gone —
        // the app in the background, the phone in a pocket — nothing collects,
        // and a session that lasts all day must not wake up once a second to
        // refresh a number nobody is reading.
        assertFalse(TunnelState.countersWatched.first())

        val screen = launch { TunnelState.counters.collect { } }
        advanceUntilIdle()
        assertTrue(TunnelState.countersWatched.first())

        screen.cancelAndJoin()
        assertFalse(TunnelState.countersWatched.first())
    }
}
