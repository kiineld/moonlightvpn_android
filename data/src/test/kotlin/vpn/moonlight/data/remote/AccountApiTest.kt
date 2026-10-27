package vpn.moonlight.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vpn.moonlight.data.model.AuthTokens
import vpn.moonlight.data.model.Pricing

class AccountApiTest {

    private class MemoryTokens(var refresh: String?) : SessionTokens {
        override suspend fun refreshToken() = refresh
        override suspend fun save(tokens: AuthTokens) { refresh = tokens.refreshToken }
        override suspend fun clear() { refresh = null }
    }

    /** A server that answers from [route]; every request it saw is in [seen]. */
    private class FakeServer(val route: (path: String, auth: String?) -> Pair<Int, String>) : Interceptor {
        val seen = mutableListOf<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val path = request.url.encodedPath.removePrefix("/api/v1")
            seen += path
            val (code, body) = route(path, request.header("Authorization"))
            return Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun api(server: FakeServer, tokens: SessionTokens, onRejected: suspend () -> Unit = {}) = AccountApi(
        baseUrl = "https://cabinet.test/api/v1",
        tokens = tokens,
        userAgent = "test",
        client = OkHttpClient.Builder().addInterceptor(server).build(),
        onSessionRejected = onRejected,
    )

    @Test
    fun `an expired access token is refreshed once and the request retried`() = runTest {
        val tokens = MemoryTokens("r1")
        val server = FakeServer { path, auth ->
            when {
                path == "/auth/refresh" -> 200 to """{"access_token":"a2","refresh_token":"r2"}"""
                path == "/me" && auth == "Bearer a2" -> 200 to """{"id":7,"balance":15000,"subscriptions":[{"id":3,"status":"active","has_link":true}]}"""
                else -> 401 to """{"error":"Token expired"}"""
            }
        }
        val me = api(server, tokens).me()
        assertEquals(7L, me.id)
        assertEquals(3L, me.subscriptions.single().id)
        assertEquals("r2", tokens.refresh)
        assertEquals(listOf("/auth/refresh", "/me"), server.seen)
    }

    @Test
    fun `a rejected refresh ends the session and says so`() = runTest {
        val tokens = MemoryTokens("revoked")
        var rejected = false
        val server = FakeServer { _, _ -> 401 to """{"error":"Refresh token revoked"}""" }
        try {
            api(server, tokens) { rejected = true }.me()
            fail("expected the session to end")
        } catch (e: AccountApiException) {
            assertTrue(e.isSessionRejected)
            assertEquals("Refresh token revoked", e.message)
        }
        assertTrue(rejected)
        assertNull(tokens.refresh)
    }

    @Test
    fun `a server error keeps the session`() = runTest {
        val tokens = MemoryTokens("r1")
        val server = FakeServer { _, _ -> 503 to """{"error":"busy"}""" }
        runCatching { api(server, tokens).me() }
        assertEquals("r1", tokens.refresh)
    }

    @Test
    fun `sign-up that needs the emailed code stores nothing`() = runTest {
        val tokens = MemoryTokens(null)
        val server = FakeServer { _, _ -> 200 to """{"status":"verification_required","email":"a@b.test"}""" }
        val result = api(server, tokens).register("a@b.test", "password1")
        assertEquals("verification_required", result.status)
        assertNull(tokens.refresh)
    }

    @Test
    fun `period prices truncate like the server`() {
        assertEquals(26_973L, Pricing.forPeriod(9_990, 90))
        assertEquals(89_910L, Pricing.forPeriod(9_990, 365))
        assertEquals(8_991L, Pricing.discounted(9_990, 10))
    }
}
