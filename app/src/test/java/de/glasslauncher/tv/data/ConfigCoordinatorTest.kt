package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.LauncherConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConfigCoordinatorTest {
    @Test fun `independent updates during pending disk write retain both changes`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val written = mutableListOf<LauncherConfig>()
        val coordinator = ConfigCoordinator(LauncherConfig()) { value ->
            if (written.isEmpty()) { entered.complete(Unit); release.await() }
            written += value
        }
        val theme = launch { coordinator.update { it.copy(theme = "zen") } }
        entered.await()
        val motion = launch { coordinator.update { it.copy(reducedMotion = true) } }
        release.complete(Unit)
        theme.join(); motion.join()
        assertEquals(2, written.size)
        assertEquals("zen", written.last().theme)
        assertTrue(written.last().reducedMotion)
        assertEquals(written.last(), coordinator.current)
    }

    @Test fun `failed save retains last successful config for next update`() = runBlocking {
        var fail = true
        val coordinator = ConfigCoordinator(LauncherConfig()) { if (fail) throw java.io.IOException("disk full") }
        try { coordinator.update { it.copy(theme = "neon") }; fail("Save must fail") }
        catch (_: java.io.IOException) { }
        assertEquals("forest", coordinator.current.theme)
        fail = false
        assertTrue(coordinator.update { it.copy(reducedMotion = true) }.reducedMotion)
        assertEquals("forest", coordinator.current.theme)
    }
}
