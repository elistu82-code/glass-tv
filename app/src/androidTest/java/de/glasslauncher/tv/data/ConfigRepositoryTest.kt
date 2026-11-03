package de.glasslauncher.tv.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.core.LauncherConfig
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfigRepositoryTest {
    private lateinit var directory: File
    private lateinit var repository: ConfigRepository

    @Before fun isolateDisk() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(target.cacheDir, "config-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir(): File = directory }
        repository = ConfigRepository(context)
    }

    @After fun removeOnlyTestFiles() { directory.deleteRecursively() }

    @Test fun savedSettingsSurviveRepositoryRecreation() = runBlocking {
        val config = LauncherConfig(theme = "neon", hidden = setOf("tv.example/.Main"), reducedMotion = true)
        repository.save(config)
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getFilesDir(): File = directory
        }
        assertEquals(config, ConfigRepository(context).load())
    }

    @Test fun invalidRestoreDoesNotChangeExistingDiskSettings() = runBlocking {
        val existing = LauncherConfig(theme = "zen")
        repository.save(existing)
        val before = File(directory, "launcher-config.json").readBytes()
        try {
            ConfigImporter.restore("""{"version":999}""") { repository.save(it) }
            fail("Unsupported version must fail before writing")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, File(directory, "launcher-config.json").readBytes())
        assertEquals(existing, repository.load())
    }

    @Test fun corruptedSettingsKeepRecoveryCopyAndUseSafeDefaults() = runBlocking {
        val broken = """{"version":1} trailing-data"""
        File(directory, "launcher-config.json").writeText(broken)
        assertEquals(LauncherConfig(), repository.load())
        assertEquals(broken, File(directory, "launcher-config.recovery.json").readText())
        repository.save(LauncherConfig(theme = "liquid"))
        assertEquals("liquid", repository.load().theme)
        assertEquals(broken, File(directory, "launcher-config.recovery.json").readText())
    }

    @Test fun outsideWallpaperPathIsNotRestored() = runBlocking {
        val outside = File(directory.parentFile, "outside-${UUID.randomUUID()}.jpg").apply { writeText("test") }
        try {
            repository.save(LauncherConfig(wallpaperPath = outside.path, wallpaperVideo = true))
            val restored = repository.load()
            assertNull(restored.wallpaperPath)
            assertFalse(restored.wallpaperVideo)
        } finally { outside.delete() }
    }
}
