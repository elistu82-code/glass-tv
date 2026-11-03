package de.glasslauncher.tv.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-lifecycle adapter. Call its lifecycle and transport methods on the main thread. */
class SpotifySessionAdapter internal constructor(private val sessions: SpotifySessions) : AutoCloseable {
    constructor(context: Context) : this(AndroidSpotifySessions(context))
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(SpotifyState())
    val state: StateFlow<SpotifyState> = mutableState.asStateFlow()
    private var started = false
    private var registered = false
    private var selected: MediaController? = null
    private class ObservedController(val controller:MediaController,val callback:MediaController.Callback) {
        var playback:PlaybackState?=null
        var metadata:MediaMetadata?=null
    }
    private val controllers = linkedMapOf<MediaSession.Token,ObservedController>()
    private val connectionChanged: (Boolean) -> Unit = { connected ->
        if (started) {
            if (connected) refresh() else unavailable("Spotify: Medienzugriff nicht verfügbar")
        }
    }

    @MainThread fun start() {
        MediaTrace.event("adapter.start", "alreadyStarted=$started")
        if (started) return
        started = true
        MediaListenerEvents.listeners.add(connectionChanged)
        refresh()
    }

    @MainThread fun stop() {
        MediaTrace.event("adapter.stop")
        started = false
        MediaListenerEvents.listeners.remove(connectionChanged)
        detach()
        mutableState.value = SpotifyState()
    }

    @MainThread override fun close() = stop()

    private fun refresh() {
        if (!started) return
        try {
            if (!registered) {
                sessions.register(::refresh)
                registered = true
            }
            val active = sessions.active()
            val tokens = active.map { it.sessionToken }.toSet()
            controllers.keys.filter { it !in tokens }.forEach { token ->
                controllers.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
            }
            active.forEach { controller ->
                if (controller.sessionToken !in controllers) {
                    lateinit var observed:ObservedController
                    val callback = object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) { observed.playback=state; publish() }
                        override fun onMetadataChanged(metadata: MediaMetadata?) {
                            observed.metadata=metadata
                            MediaTrace.event("controller.metadata.changed", "present=${metadata!=null}")
                            publish()
                        }
                        override fun onSessionDestroyed() {
                            controllers.remove(controller.sessionToken)?.let { old ->
                                old.controller.unregisterCallback(old.callback)
                            }
                            publish()
                        }
                    }
                    observed=ObservedController(controller,callback)
                    controllers[controller.sessionToken]=observed
                    controller.registerCallback(callback,handler)
                    // Initial snapshot once; subsequent position updates must not re-parcel bitmaps.
                    observed.playback=controller.playbackState
                    observed.metadata=controller.metadata
                    MediaTrace.event("controller.callback.registered")
                }
            }
            publish()
        } catch (_: SecurityException) {
            unavailable("Spotify: Medienzugriff nicht freigegeben")
        } catch (_: RuntimeException) {
            unavailable("Spotify: Mediensitzung nicht verfügbar")
        }
    }

    private fun publish() {
        if (!started) return
        try {
            val observed = controllers.values
                .filter { sessionCapabilities(it.playback?.state ?: PlaybackState.STATE_NONE,0).active }
                .sortedByDescending { it.playback?.state == PlaybackState.STATE_PLAYING }
                .firstOrNull()
            if (observed == null) {
                selected = null
                mutableState.value = SpotifyState()
                return
            }
            val controller=observed.controller
            val previous = mutableState.value.takeIf { selected?.sessionToken == controller.sessionToken }
            selected = controller
            val playback = observed.playback
            val caps = sessionCapabilities(playback?.state ?: PlaybackState.STATE_NONE, playback?.actions ?: 0)
            val metadata = observed.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().take(500)
            val artist = (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty().take(500)
            val art = embeddedArtwork(metadata)
            if (de.glasslauncher.tv.BuildConfig.DEBUG) MediaTrace.distinct("controller.publish", "playback=${playback?.state} title=${title.isNotBlank()} artist=${artist.isNotBlank()} embedded=${art != null} uri=${sequenceOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).any { !metadata?.getString(it).isNullOrBlank() }}")
            // A paused session may temporarily omit its metadata; retain only that same track's art.
            val retain = playback?.state == PlaybackState.STATE_PAUSED &&
                (metadata == null || (title == previous?.title && artist == previous?.artist))
            val artwork = art ?: previous?.artwork?.takeIf { retain }
            val artworkKey = if (art != null) "${controller.sessionToken.hashCode()}:${art.generationId}:$title:$artist"
                else previous?.artworkKey?.takeIf { retain }
            mutableState.value = SpotifyState(
                title = if (metadata == null && retain) previous?.title.orEmpty() else title,
                artist = if (metadata == null && retain) previous?.artist.orEmpty() else artist,
                artwork = artwork, artworkKey = artworkKey, isPlaying = caps.playing,
                positionMs=playback?.position ?: -1,
                durationMs=metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it>0 } ?: previous?.durationMs?.takeIf { retain } ?: 0,
                positionUpdatedAtMs=playback?.lastPositionUpdateTime ?: 0,
                playbackSpeed=playback?.playbackSpeed ?: 0f,
                connected = true, canPlayPause = caps.playPause, canPrevious = caps.previous,
                canNext = caps.next, status = if (caps.playing) "Spotify spielt" else "Spotify pausiert / bereit",
            )
        } catch (_: SecurityException) {
            unavailable("Spotify: Medienzugriff nicht freigegeben")
        } catch (_: RuntimeException) {
            unavailable("Spotify: Mediensitzung nicht verfügbar")
        }
    }

    /** Accept bounded embedded bitmaps only. Never resolve artwork/content/network URIs. */
    private fun embeddedArtwork(metadata: MediaMetadata?): Bitmap? = sequenceOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON,
    ).mapNotNull { metadata?.getBitmap(it) }.firstOrNull {
        !it.isRecycled && it.width in 1..1024 && it.height in 1..1024 && it.allocationByteCount <= 4 * 1024 * 1024
    }

    private fun unavailable(status: String) {
        MediaTrace.event("adapter.unavailable", "accessDenied=${status.contains("nicht freigegeben")}")
        detach()
        mutableState.value = SpotifyState(status = status)
    }

    private fun detach() {
        controllers.values.forEach { observed ->
            try { observed.controller.unregisterCallback(observed.callback) } catch (_: RuntimeException) { /* Session already gone. */ }
        }
        controllers.clear()
        selected = null
        if (registered) {
            try { sessions.unregister() }
            catch (_: RuntimeException) { /* Access may have been revoked. */ }
        }
        registered = false
    }

    @MainThread fun playPause(): Boolean = control("playPause") { controller, caps ->
        if (!caps.playPause) false else {
            val action = if (caps.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
            if ((controller.playbackState?.actions ?: 0) and action != 0L) {
                if (caps.playing) controller.transportControls.pause() else controller.transportControls.play()
                true
            } else {
                // A session may advertise only the toggle action, without explicit play/pause.
                val now = SystemClock.uptimeMillis()
                val down = controller.dispatchMediaButtonEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
                val up = controller.dispatchMediaButtonEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
                down || up
            }
        }
    }

    @MainThread fun previous(): Boolean = control("previous") { controller, caps ->
        if (!caps.previous) false else { controller.transportControls.skipToPrevious(); true }
    }

    @MainThread fun next(): Boolean = control("next") { controller, caps ->
        if (!caps.next) false else { controller.transportControls.skipToNext(); true }
    }

    private inline fun control(name:String,command: (MediaController, SessionCapabilities) -> Boolean): Boolean {
        if (!started) return false
        val controller = selected ?: return false
        return try {
            val playback = controller.playbackState
            command(controller, sessionCapabilities(playback?.state ?: PlaybackState.STATE_NONE, playback?.actions ?: 0)).also {
                MediaTrace.event("controller.command", "kind=$name sent=$it playback=${playback?.state}")
            }
        } catch (_: SecurityException) {
            unavailable("Spotify: Medienzugriff nicht freigegeben"); false
        } catch (_: RuntimeException) {
            unavailable("Spotify: Steuerung nicht verfügbar"); false
        }
    }
}
