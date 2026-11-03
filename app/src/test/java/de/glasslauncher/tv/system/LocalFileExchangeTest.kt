package de.glasslauncher.tv.system

import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.data.ConfigCodec
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LocalFileExchangeTest {
    private val root = Files.createTempDirectory("glass-exchange-test").toFile()
    private val exchange = LocalFileExchange(root)
    @After fun cleanup() { root.deleteRecursively() }

    @Test fun listsOnlySupportedDirectFilesAndRejectsEscapingLinks() {
        val folder = File(root, "imports").apply { mkdirs() }
        File(folder, "Forest.PNG").writeText("fixture")
        File(folder, "settings.json").writeText("{}")
        File(folder, "folder.png").mkdir()
        val outside = File(root, "outside.png").apply { writeText("outside") }
        Files.createSymbolicLink(File(folder, "escape.png").toPath(), outside.toPath())
        assertEquals(listOf("Forest.PNG"), exchange.listImports(setOf("png", "jpg")).map { it.name })
        assertEquals(listOf("settings.json"), exchange.listImports(setOf("json")).map { it.name })
    }

    @Test fun exportsValidatedNonSecretSettingsWithoutWallpaperPaths() {
        val config = LauncherConfig(theme = "neon", wallpaperPath = "/private/wallpapers/clip.mp4", wallpaperVideo = true)
        val exported = exchange.exportConfig(ConfigCodec.encode(config))
        assertEquals(File(root, "exports").canonicalFile, exported.canonicalFile.parentFile)
        assertEquals(config.copy(wallpaperPath = null, wallpaperVideo = false), ConfigCodec.decode(exported.readText()))
        assertTrue(exported.name.endsWith(".json"))
    }

    @Test fun invalidExportLeavesExistingFilesUntouched() {
        val exports = File(root, "exports").apply { mkdirs() }
        val existing = File(exports, "previous.json").apply { writeText("keep") }
        assertThrows(IllegalArgumentException::class.java) { exchange.exportConfig("""{"version":99}""") }
        assertEquals(listOf(existing), exports.listFiles()!!.toList())
        assertEquals("keep", existing.readText())
    }
}
