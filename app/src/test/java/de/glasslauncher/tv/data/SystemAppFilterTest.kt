package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherConfig
import org.junit.Assert.*
import org.junit.Test

class SystemAppFilterTest {
    private fun app(pkg: String, system: Boolean = true) = InstalledApp("$pkg/.Main", pkg, pkg, isSystem = system)

    @Test fun knownTechnicalToolsAreHiddenByDefault() {
        val tools = listOf(app("com.android.settings"), app("com.amazon.tv.settings.v2"), app("com.android.systemui"))
        assertTrue(AppOrdering.visibleApps(tools, LauncherConfig()).isEmpty())
    }

    @Test fun androidTvTechnicalEntryPointsAreHiddenButNormalTvAppsRemainVisible() {
        val technical = listOf(app("com.android.tv.settings"), app("com.google.android.tv.remote.service"))
        val normal = listOf(app("com.android.vending"), app("com.google.android.play.games"), app("com.google.android.youtube.tv"))
        assertEquals(normal.toSet(), AppOrdering.visibleApps(technical + normal, LauncherConfig()).toSet())
        val restore = LauncherConfig(shownSystemApps = technical.map { it.id }.toSet())
        assertEquals(technical.toSet(), AppOrdering.visibleApps(technical, restore).toSet())
        assertEquals(2, AppOrdering.visibleApps(technical.map { it.copy(isSystem = false) }, LauncherConfig()).size)
    }

    @Test fun realFireTroubleshootingIsHiddenRegardlessOfOldOrderAndPins() {
        val tool=app("com.amazon.firetv.troubleshooting")
        val normal=listOf(app("com.amazon.avod.thirdpartyclient"),app("com.netflix.ninja"),app("com.amazon.firetv.youtube"),app("com.spotify.tv.android",false),app("org.jellyfin.androidtv",false),app("com.amazon.firebat"))
        val saved=LauncherConfig(order=listOf(tool.id)+normal.map { it.id }, pinned=setOf(tool.id), hidden=setOf(normal[1].id), shownSystemApps=setOf("com.android.settings/.Main"))
        assertEquals(normal.filterNot { it.id in saved.hidden }.toSet(),AppOrdering.visibleApps(listOf(tool)+normal,saved).toSet())
        assertTrue(AppOrdering.visibleApps(listOf(tool),saved.copy(shownSystemApps=setOf(tool.id))).isNotEmpty())
        assertTrue(AppOrdering.visibleApps(listOf(tool),saved.copy(hidden=setOf(tool.id),shownSystemApps=setOf(tool.id))).isEmpty())
    }

    @Test fun auditedFireConfigurationToolsAreHiddenButCanBeExplicitlyRestored() {
        val tools=listOf("com.amazon.whasettings","com.amazon.tv.earlyaccess","com.amazon.ftv.profilepicker").map { app(it) }
        assertTrue(AppOrdering.visibleApps(tools,LauncherConfig()).isEmpty())
        val restored=LauncherConfig(shownSystemApps=tools.map { it.id }.toSet())
        assertEquals(tools.toSet(),AppOrdering.visibleApps(tools,restored).toSet())
    }

    @Test fun streamingAndUserInstalledAppsRemainVisible() {
        val normal = listOf(app("com.amazon.avod.thirdpartyclient"), app("com.amazon.venezia"), app("com.amazon.amazonvideo.livingroom"), app("com.android.settings", false), app("example.streaming", false))
        assertEquals(normal.map { it.id }.toSet(), AppOrdering.visibleApps(normal, LauncherConfig()).map { it.id }.toSet())
    }

    @Test fun allKnownToolsCanBeRestoredWithoutBlanketVendorFiltering() {
        val tools = SystemAppPolicy.technicalPackages.map { app(it) }
        val normal = listOf(app("com.amazon.spiderpork"), app("com.amazon.firetv.youtube"), app("com.amazon.new.streaming"))
        assertEquals(normal.toSet(), AppOrdering.visibleApps(tools + normal, LauncherConfig()).toSet())
        val restored = LauncherConfig(shownSystemApps = tools.map { it.id }.toSet())
        assertEquals(tools.map { it.id }.toSet(), AppOrdering.visibleApps(tools, restored).map { it.id }.toSet())
    }

    @Test fun explicitSystemToolOverrideSurvivesBackupAndManualHideStillWins() {
        val tool = app("com.android.settings")
        val config = LauncherConfig(shownSystemApps = setOf(tool.id), showMusicInfo = false)
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
        assertEquals(listOf(tool), AppOrdering.visibleApps(listOf(tool), config))
        assertTrue(AppOrdering.visibleApps(listOf(tool), config.copy(hidden = setOf(tool.id))).isEmpty())
    }
}
