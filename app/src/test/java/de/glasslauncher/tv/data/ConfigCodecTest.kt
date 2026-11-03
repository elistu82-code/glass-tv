package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.LauncherConfig
import org.junit.Assert.*
import org.junit.Test

class ConfigCodecTest {
    @Test fun `sound preferences default on and survive restore with validation`() {
        val defaults = com.google.gson.JsonParser.parseString(ConfigCodec.encode(LauncherConfig())).asJsonObject
        assertTrue("Sound preference must be exported", defaults.has("uiSounds"))
        assertTrue(defaults["uiSounds"].asBoolean)
        val restored = com.google.gson.JsonParser.parseString(ConfigCodec.encode(ConfigCodec.decode("""{"uiSounds":false,"uiSoundIntensity":0.75}"""))).asJsonObject
        assertFalse(restored["uiSounds"].asBoolean)
        assertEquals(.75, restored["uiSoundIntensity"].asDouble, .0001)
        rejects("""{"uiSounds":"true"}""")
        rejects("""{"uiSoundIntensity":"loud"}""")
        assertEquals(LauncherConfig(), ConfigCodec.decode("""{"version":1}"""))
    }

    @Test fun `configuration round trips without losing selections`() {
        val config = LauncherConfig(order = listOf("tv.example/.Main"), pinned = setOf("tv.example/.Main"), hidden = setOf("tv.hidden/.Main"), theme = "neon", wallpaperPath = "/private/wallpapers/custom.mp4", wallpaperVideo = true, animationSpeed = 1.5f, reducedMotion = true, glassOpacity = .4f, easing = "linear", musicMode = true, simulatedVisualizer = true)
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test fun `legacy version zero migrates with current defaults`() {
        val config = ConfigCodec.decode("""{"version":0,"order":["tv.example/.Main"]}""")
        assertEquals(1, config.version)
        assertEquals(listOf("tv.example/.Main"), config.order)
        assertEquals("forest", config.theme)
    }

    @Test fun `duplicate IDs and unsupported theme normalize safely`() {
        val config = ConfigCodec.decode("""{"order":["tv.example/.Main","tv.example/.Main"],"theme":"unknown","animationSpeed":99,"glassOpacity":-1,"easing":"bounce"}""")
        assertEquals(listOf("tv.example/.Main"), config.order)
        assertEquals("forest", config.theme)
        assertEquals(2f, config.animationSpeed)
        assertEquals(.1f, config.glassOpacity)
        assertEquals("smooth", config.easing)
    }

    @Test fun `malformed JSON and unsupported versions fail before restore`() {
        rejects("not-json")
        rejects("""{"version":2}""")
        rejects("""{"version":-1}""")
        rejects("""{"order":"bad"}""")
        rejects("""{"order":[42]}""")
        rejects("""{"reducedMotion":"true"}""")
        rejects("""{"animationSpeed":"1"}""")
        rejects("""{"order":["../escape"]}""")
    }

    @Test fun `oversized document and huge app lists fail`() {
        rejects(" ".repeat(ConfigCodec.MAX_BYTES + 1))
        rejects("""{"order":[${(0..1000).joinToString(",") { "\"tv.app$it/.Main\"" }}]}""")
    }

    @Test fun `trailing content after JSON object is rejected`() {
        rejects("""{"version":1} garbage""")
        rejects("""{"version":1}{"version":0}""")
    }

    @Test fun `single quoted JSON string is rejected`() {
        rejects("""{"theme":'forest'}""")
    }

    @Test fun `unquoted JSON key is rejected`() {
        rejects("""{version:1}""")
    }

    @Test fun `trailing comma is rejected`() {
        rejects("""{"version":1,}""")
        rejects("""{"order":["tv.example/.Main",]}""")
    }

    @Test fun `invalid version types and fractional versions are rejected`() {
        for (value in listOf("null", "\"1\"", "true", "[]", "{}", "0.5", "1.5", "1.000000000000000000001", "-1", "2", "999999999999999999999999")) {
            rejects("""{"version":$value}""")
        }
    }

    @Test fun `duplicate fields are rejected rather than silently overwritten`() {
        rejects("""{"version":2,"version":1}""")
        rejects("""{"theme":"neon","theme":"forest"}""")
    }

    @Test fun `JSON comments control characters and invalid numeric tokens are rejected`() {
        rejects("""{/* comment */"version":1}""")
        rejects("{\"theme\":\"for\nest\"}")
        rejects("""{"animationSpeed":NaN}""")
        rejects("""{"animationSpeed":01}""")
    }

    @Test fun `valid relative full and nested class component IDs round trip`() {
        val ids = listOf("tv.example/.Main", "tv.example/tv.example.Main", "tv.example/tv.example.Main\$Nested")
        assertEquals(ids, ConfigCodec.decode(ConfigCodec.encode(LauncherConfig(order = ids))).order)
    }

    @Test fun `traversal and empty component segments are rejected`() {
        for (id in listOf("../escape", "tv..example/.Main", "tv.example/..Main", "tv.example/", "/.Main", "tv.example/.Main/extra")) {
            rejects("""{"order":["$id"]}""")
        }
    }

    private fun rejects(json: String) {
        try { ConfigCodec.decode(json); fail("Expected configuration rejection: ${json.take(100)}") }
        catch (_: IllegalArgumentException) { /* Expected, caller retains current config. */ }
    }
}
