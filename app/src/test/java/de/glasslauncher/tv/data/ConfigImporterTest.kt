package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.LauncherConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConfigImporterTest {
    @Test fun `invalid imports never call persistence or replace existing settings`() = runBlocking {
        val existing = LauncherConfig(theme = "neon", animationSpeed = 1.5f)
        for (json in listOf("not-json", """{"version":2}""", """{"order":["../escape"]}""", """{"version":1} garbage""")) {
            var persisted = existing
            var displayed = existing
            var writes = 0
            try {
                displayed = ConfigImporter.restore(json) { writes++; persisted = it }
                fail("Invalid import was accepted: $json")
            } catch (_: IllegalArgumentException) { }
            assertEquals(0, writes)
            assertEquals(existing, persisted)
            assertEquals(existing, displayed)
        }
    }

    @Test fun `valid import is persisted once with external wallpaper cleared`() = runBlocking {
        val imported = LauncherConfig(theme = "zen", wallpaperPath = "/outside/private.mp4", wallpaperVideo = true)
        var writes = 0
        var persisted: LauncherConfig? = null
        val result = ConfigImporter.restore(ConfigCodec.encode(imported)) { writes++; persisted = it }
        assertEquals(1, writes)
        assertEquals(imported.copy(wallpaperPath = null, wallpaperVideo = false), result)
        assertEquals(result, persisted)
    }

    @Test fun `failed persistence cannot publish imported settings`() = runBlocking {
        val existing = LauncherConfig(theme = "neon")
        var displayed = existing
        val diskFailure = java.io.IOException("disk unavailable")
        try {
            displayed = ConfigImporter.restore("""{"version":1,"theme":"zen"}""") { throw diskFailure }
            fail("Disk failure must propagate")
        } catch (failure: java.io.IOException) { assertSame(diskFailure, failure) }
        assertEquals(existing, displayed)
    }
}
