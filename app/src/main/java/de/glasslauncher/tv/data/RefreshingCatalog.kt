package de.glasslauncher.tv.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Single-flight snapshot; invalidation racing a load remains pending for the next request. */
internal class RefreshingCatalog<T>(private val load: suspend () -> List<T>) {
    private val lock=Mutex()
    private val generation=AtomicLong(0)
    @Volatile private var loadedGeneration = -1L
    private var snapshot: List<T>? = null
    val isStale get() = loadedGeneration != generation.get()
    fun invalidate() { generation.incrementAndGet() }
    suspend fun get(): List<T> = lock.withLock {
        if (!isStale) return@withLock requireNotNull(snapshot)
        val version=generation.get()
        val result=load() // Cancellation never publishes a partial snapshot.
        snapshot=result
        loadedGeneration=version
        result
    }
}
