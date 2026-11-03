package de.glasslauncher.tv.wallpaper

import android.graphics.Bitmap
import de.glasslauncher.tv.system.measured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Retain only the most recent image and blur; never an unbounded theme/album history. */
internal object WallpaperMemory {
    private val lock=Mutex()
    @Volatile private var entry: Entry?=null
    private data class Entry(val key: String,val normal: Bitmap,val blur: Bitmap?=null,val musicBlur:Bitmap?=null)
    fun cached(key: String,blur: Boolean=false): Bitmap? = entry?.takeIf { it.key==key }?.let { if(blur) it.blur else it.normal }
    fun cachedMusic(key:String):Bitmap?=entry?.takeIf { it.key==key }?.musicBlur
    suspend fun musicBlurred(key:String,image:Bitmap):Bitmap=withContext(Dispatchers.IO) {
        lock.withLock { cachedMusic(key) ?: measured("Glass.musicBlur") { musicBlurFrame(image) }.also { result ->
            entry?.takeIf { it.key==key && it.normal===image }?.let { entry=it.copy(musicBlur=result) }
        } }
    }
    suspend fun image(key: String,load: () -> Bitmap): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock { cached(key) ?: measured("Glass.wallpaper") { load() }.also { entry=Entry(key,it) } }
    }
    suspend fun blurred(key: String,image: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock { cached(key,true) ?: measured("Glass.blur") { blurFrame(image) }.also { result ->
            entry?.takeIf { it.key==key && it.normal===image }?.let { entry=it.copy(blur=result) }
        } }
    }
}
