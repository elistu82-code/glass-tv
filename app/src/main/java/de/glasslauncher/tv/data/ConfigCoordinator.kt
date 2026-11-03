package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.LauncherConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Apply each edit to the last committed state, even while another disk write is suspended. */
class ConfigCoordinator(initial: LauncherConfig, private val persist: suspend (LauncherConfig) -> Unit) {
    private val lock = Mutex()
    var current = initial
        private set

    suspend fun update(change: (LauncherConfig) -> LauncherConfig): LauncherConfig = lock.withLock {
        val next = ConfigCodec.decode(ConfigCodec.encode(change(current)))
        persist(next)
        current = next
        next
    }

    suspend fun restore(json: String): LauncherConfig = lock.withLock {
        val next = ConfigImporter.restore(json, persist)
        current = next
        next
    }
}
