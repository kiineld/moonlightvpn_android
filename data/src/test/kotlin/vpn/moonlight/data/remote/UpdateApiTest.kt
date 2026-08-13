package vpn.moonlight.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `orders versions numerically, not as text`() {
        // The whole point: "1.0.10" < "1.0.9" as strings.
        assertTrue(AppVersion.isNewer("1.0.10", "1.0.9"))
        assertFalse(AppVersion.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `ignores a leading v`() {
        assertTrue(AppVersion.isNewer("v1.1.0", "1.0.9"))
        assertEquals(0, AppVersion.compare("v1.0.9", "1.0.9"))
    }

    @Test
    fun `an equal version is not newer`() {
        assertFalse(AppVersion.isNewer("1.0.9", "1.0.9"))
    }

    @Test
    fun `treats missing components as zero`() {
        assertEquals(0, AppVersion.compare("1.0", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun `compares only the leading numeric run`() {
        assertEquals(0, AppVersion.compare("1.0.9-beta", "1.0.9"))
        assertTrue(AppVersion.isNewer("1.1.0-rc1", "1.0.9"))
    }

    @Test
    fun `an unparseable version never looks newer`() {
        assertFalse(AppVersion.isNewer("nightly", "1.0.9"))
    }
}

class ReleaseAssetsTest {

    private fun asset(name: String) = ReleaseAsset(name, "https://example/$name", 1)

    private val assets = listOf(
        asset("moonlight-android-arm64-v8a.apk"),
        asset("moonlight-android-armeabi-v7a.apk"),
        asset("moonlight-android-x86_64.apk"),
        asset("moonlight-android-universal.apk"),
    )

    @Test
    fun `prefers the device abi over the universal build`() {
        val picked = ReleaseAssets.forAbis(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("moonlight-android-arm64-v8a.apk", picked?.name)
    }

    @Test
    fun `follows the abi preference order of the device`() {
        val picked = ReleaseAssets.forAbis(assets, listOf("armeabi-v7a"))
        assertEquals("moonlight-android-armeabi-v7a.apk", picked?.name)
    }

    @Test
    fun `falls back to universal for an abi with no split apk`() {
        val picked = ReleaseAssets.forAbis(assets, listOf("riscv64"))
        assertEquals("moonlight-android-universal.apk", picked?.name)
    }

    @Test
    fun `ignores attachments that are not apks`() {
        val picked = ReleaseAssets.forAbis(
            listOf(asset("checksums.txt"), asset("mapping.zip")),
            listOf("arm64-v8a"),
        )
        assertNull(picked)
    }
}

class UpdateApiParseTest {

    private val api = UpdateApi(repository = "kiineld/moonlightvpn_android", userAgent = "test")

    private val body = """
        {
          "tag_name": "v1.1.0",
          "name": "v1.1.0",
          "draft": false,
          "body": "Fixes the thing",
          "assets": [
            {
              "name": "moonlight-android-arm64-v8a.apk",
              "size": 34660484,
              "browser_download_url": "https://github.com/x/releases/download/v1.1.0/moonlight-android-arm64-v8a.apk"
            },
            {
              "name": "moonlight-android-universal.apk",
              "size": 114852652,
              "browser_download_url": "https://github.com/x/releases/download/v1.1.0/moonlight-android-universal.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `reads the version, notes and the asset for this device`() {
        val release = api.parse(body, listOf("arm64-v8a"))!!
        assertEquals("1.1.0", release.versionName)
        assertEquals("Fixes the thing", release.notes)
        assertEquals("moonlight-android-arm64-v8a.apk", release.asset.name)
        assertEquals(34660484L, release.asset.sizeBytes)
    }

    @Test
    fun `strips the leading v from the tag`() {
        assertEquals("1.1.0", api.parse(body, listOf("x86_64"))!!.versionName)
    }

    @Test
    fun `returns nothing for a release with no apk`() {
        val noApk = """{"tag_name":"v1.1.0","assets":[{"name":"notes.txt","size":1,"browser_download_url":"https://x/notes.txt"}]}"""
        assertNull(api.parse(noApk, listOf("arm64-v8a")))
    }

    @Test
    fun `returns nothing for a draft`() {
        val draft = body.replace("\"draft\": false", "\"draft\": true")
        assertNull(api.parse(draft, listOf("arm64-v8a")))
    }

    @Test
    fun `survives a malformed body`() {
        assertNull(api.parse("not json", listOf("arm64-v8a")))
        assertNull(api.parse("", listOf("arm64-v8a")))
        assertNull(api.parse("[]", listOf("arm64-v8a")))
    }

    @Test
    fun `treats a blank release note as none`() {
        val blank = body.replace("\"Fixes the thing\"", "\"   \"")
        assertNull(api.parse(blank, listOf("arm64-v8a"))!!.notes)
    }
}
