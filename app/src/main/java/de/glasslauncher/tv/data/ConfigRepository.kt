package de.glasslauncher.tv.data

import android.content.Context
import android.util.AtomicFile
import de.glasslauncher.tv.core.LauncherConfig
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Private atomic settings. A broken file is retained for recovery, never exported automatically. */
class ConfigRepository(context: Context) {
    private val directory = context.filesDir
    private val file = AtomicFile(File(directory, "launcher-config.json"))
    private val lock = Mutex()

    suspend fun load(): LauncherConfig = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withLock LauncherConfig()
            try {
                val json = file.openRead().use { input ->
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        require(bytes.size() + read <= ConfigCodec.MAX_BYTES) { "Settings exceed size limit" }
                        bytes.write(buffer, 0, read)
                    }
                    bytes.toString(Charsets.UTF_8.name())
                }
                safeWallpaper(ConfigCodec.decode(json))
            } catch (_: Exception) {
                // Preserve one bounded recovery copy before a later successful save replaces config.
                runCatching {
                    val recovery = File(directory, "launcher-config.recovery.json")
                    if (file.baseFile.isFile && file.baseFile.length() <= ConfigCodec.MAX_BYTES) {
                        file.baseFile.copyTo(recovery, overwrite = true)
                    }
                }
                LauncherConfig()
            }
        }
    }

    suspend fun save(config: LauncherConfig) = withContext(Dispatchers.IO) {
        lock.withLock {
            val safe = safeWallpaper(ConfigCodec.decode(ConfigCodec.encode(config)))
            val bytes = ConfigCodec.encode(safe).toByteArray(Charsets.UTF_8)
            require(bytes.size <= ConfigCodec.MAX_BYTES)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }
    }

    private fun safeWallpaper(config: LauncherConfig): LauncherConfig {
        val path = config.wallpaperPath ?: return config.copy(wallpaperVideo = false)
        val valid = runCatching {
            val root = File(directory, "wallpapers").canonicalFile
            val image = File(path).canonicalFile
            image.path.startsWith(root.path + File.separator) && image.isFile
        }.getOrDefault(false)
        return if (valid) config else config.copy(wallpaperPath = null, wallpaperVideo = false)
    }
}
