package de.glasslauncher.tv.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.system.measured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Bounded process cache, shared by dock and transition; no drawable inflation on the UI thread. */
internal object AppIcons {
    private val cache=object : LruCache<String,Bitmap>(6*1024) {
        override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount/1024).coerceAtLeast(1)
    }
    private val loadLock=Mutex()
    private fun key(app: InstalledApp)="${app.id}|${app.iconRevision}|${app.icon?.let(System::identityHashCode) ?: 0}"
    fun cached(app: InstalledApp): Bitmap? = cache.get(key(app))
    fun invalidatePackage(pkg: String) { cache.snapshot().keys.filter { it.startsWith("$pkg/") }.forEach(cache::remove) }
    fun clear() = cache.evictAll()
    suspend fun load(context: Context, app: InstalledApp): Bitmap? = withContext(Dispatchers.IO) {
        loadLock.withLock {
            cached(app)?.let { return@withLock it }
            val bitmap=runCatching { measured("Glass.icon") {
                val drawable=app.icon ?: context.packageManager.getActivityIcon(requireNotNull(ComponentName.unflattenFromString(app.id)))
                val w=drawable.intrinsicWidth.takeIf { it>0 } ?: 96
                val h=drawable.intrinsicHeight.takeIf { it>0 } ?: 96
                val scale=min(448f/w,176f/h)
                drawable.toBitmap((w*scale).roundToInt().coerceAtLeast(1),(h*scale).roundToInt().coerceAtLeast(1))
            } }.getOrNull()
            bitmap?.also { cache.put(key(app),it) }
        }
    }
}
