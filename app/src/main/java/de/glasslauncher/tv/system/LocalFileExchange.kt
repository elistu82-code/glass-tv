package de.glasslauncher.tv.system

import de.glasslauncher.tv.data.ConfigCodec
import java.io.File
import java.util.Locale
import java.util.UUID

/** Restricted exchange folders for Fire OS devices without a system document picker. */
class LocalFileExchange(private val root: File) {
    val imports: File get() = File(root, "imports").apply { check(isDirectory || mkdirs()) }

    fun listImports(extensions: Set<String>): List<File> {
        val directory = imports.canonicalFile
        return directory.listFiles().orEmpty().filter {
            it.isFile && it.canonicalFile.parentFile == directory && it.extension.lowercase(Locale.ROOT) in extensions
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun exportConfig(json: String): File {
        // Validate before creating any destination; never export private wallpaper paths.
        val safe = ConfigCodec.decode(json).copy(wallpaperPath = null, wallpaperVideo = false)
        val directory = File(root, "exports").apply { check(isDirectory || mkdirs()) }
        val destination = File(directory, "glass-tv-settings-${UUID.randomUUID().toString().take(8)}.json")
        val temporary = File(directory, "${destination.name}.tmp")
        try {
            temporary.outputStream().use { output ->
                output.write(ConfigCodec.encode(safe).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(destination)) { "Export konnte nicht gespeichert werden" }
            return destination
        } finally { temporary.delete() }
    }
}
