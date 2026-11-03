package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherConfig
import org.junit.Assert.*
import org.junit.Test

class AppOrderingTest {
    private fun app(name: String) = InstalledApp("tv.$name/.Main", "tv.$name", name)

    @Test fun `hidden apps stay hidden and newly installed apps follow saved order`() {
        val apps = listOf(app("B"), app("A"), app("C"))
        val config = LauncherConfig(order = listOf(app("C").id, "removed/.Main", app("A").id), hidden = setOf(app("A").id))
        assertEquals(listOf("C", "B"), AppOrdering.visibleApps(apps, config).map { it.label })
    }

    @Test fun `duplicate saved IDs do not duplicate visible applications`() {
        val apps = listOf(app("B"), app("A"), app("A"))
        assertEquals(listOf("B", "A"), AppOrdering.visibleApps(apps, LauncherConfig(order = listOf(app("B").id, app("B").id))).map { it.label })
    }

    @Test fun `move clamps row boundary and leaves missing selection unchanged`() {
        assertEquals(listOf("a", "b", "c"), AppOrdering.move(listOf("a", "b", "c"), "a", -1))
        assertEquals(listOf("b", "a", "c"), AppOrdering.move(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("a", "c", "b"), AppOrdering.move(listOf("a", "b", "c"), "b", 99))
        assertEquals(listOf("a"), AppOrdering.move(listOf("a"), "missing", 1))
    }

    @Test fun `pinning does not silently reorder apps`() {
        assertEquals(listOf("A", "B"), AppOrdering.visibleApps(listOf(app("B"), app("A")), LauncherConfig(pinned = setOf(app("B").id))).map { it.label })
    }
}
