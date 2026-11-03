package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.LauncherConfig

/** Validate completely before persistence; callers publish the returned settings only on success. */
object ConfigImporter {
    suspend fun restore(json: String, persist: suspend (LauncherConfig) -> Unit): LauncherConfig {
        val validated = ConfigCodec.decode(json).copy(wallpaperPath = null, wallpaperVideo = false)
        persist(validated)
        return validated
    }
}
