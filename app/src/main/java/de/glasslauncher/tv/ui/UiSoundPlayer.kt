package de.glasslauncher.tv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.provider.Settings
import de.glasslauncher.tv.BuildConfig
import de.glasslauncher.tv.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** Preloaded original short samples, mixed into media only when native system effects are muted. */
internal class UiSoundPlayer(private val context:Context):AutoCloseable {
    private val audio=context.getSystemService(AudioManager::class.java)
    private val loaded=ConcurrentHashMap.newKeySet<Int>()
    private val prepared=CompletableDeferred<Boolean>()
    private val pool=SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val navigation:Int
    private val confirmation:Int
    private var lastStream=0
    private var closed=false
    private var cachedRoute:UiSoundRoute?=null
    private var checkedAt=0L
    init {
        pool.setOnLoadCompleteListener { _,id,status ->
            if(status==0) { loaded.add(id);if(loaded.size==2) prepared.complete(true) }
            else prepared.complete(false)
        }
        navigation=pool.load(context,R.raw.ui_navigation,1)
        confirmation=pool.load(context,R.raw.ui_confirm,1)
        if(navigation==0 || confirmation==0) prepared.complete(false)
    }
    suspend fun prepare():Boolean=withTimeoutOrNull(2000) { prepared.await() } ?: false
    /** Queries stay on the audio worker; platform routing/muting still applies between refreshes. */
    @Synchronized fun route():UiSoundRoute {
        val now=SystemClock.uptimeMillis()
        if(cachedRoute==null || now-checkedAt>=250) {
            val next=runCatching {
                uiSoundRoute(Settings.System.getInt(context.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,0)==1,
                    audio!=null && !audio.isStreamMute(AudioManager.STREAM_SYSTEM) && audio.getStreamVolume(AudioManager.STREAM_SYSTEM)>0,
                    audio!=null && !audio.isStreamMute(AudioManager.STREAM_MUSIC) && audio.getStreamVolume(AudioManager.STREAM_MUSIC)>0)
            }.getOrDefault(UiSoundRoute.SILENT)
            if(next!=cachedRoute && BuildConfig.DEBUG) android.util.Log.d("GlassSound","route=$next samplesReady=${loaded.size==2}")
            cachedRoute=next;checkedAt=now
        }
        return cachedRoute ?: UiSoundRoute.SILENT
    }
    /** >0: a real SoundPool stream; -1: native dispatch; 0: intentionally silent/not ready. */
    @Synchronized fun play(sound:UiSound,intensity:Float):Int {
        if(closed || !intensity.isFinite() || intensity<=0f) return 0
        val gain=intensity.coerceIn(0f,1f).let { it*it*.65f }
        return runCatching { when(route()) {
            UiSoundRoute.SILENT -> 0
            UiSoundRoute.NATIVE -> {
                val effect=when(sound) {
                    UiSound.LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
                    UiSound.RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
                    UiSound.UP -> AudioManager.FX_FOCUS_NAVIGATION_UP
                    UiSound.DOWN -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
                    UiSound.CONFIRM -> AudioManager.FX_KEY_CLICK
                }
                audio?.playSoundEffect(effect,gain);-1
            }
            UiSoundRoute.MEDIA -> {
                val sample=if(sound==UiSound.CONFIRM) confirmation else navigation
                if(sample !in loaded) 0 else {
                    // One voice only: every actual move responds immediately without a growing mix/queue.
                    if(lastStream!=0) pool.stop(lastStream)
                    pool.play(sample,gain,gain,1,0,1f).also { lastStream=it }
                }
            }
        } }.getOrDefault(0)
    }
    @Synchronized override fun close() {
        if(!closed) { closed=true;prepared.complete(false);pool.release();loaded.clear() }
    }
}
