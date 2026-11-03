package de.glasslauncher.tv.wallpaper

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView

internal enum class VideoPlaybackState { RELEASED, PREPARING, PLAYING, ERROR }

/** One foreground wallpaper session. All calls and MediaPlayer callbacks belong to the main thread. */
internal class VideoPlaybackController(
    private val path: String,
    private val onState: (VideoPlaybackState) -> Unit,
) : TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var output: Surface? = null
    private var texture: SurfaceTexture? = null
    private var view: TextureView? = null
    private var active = false
    private var prepared = false
    private var savedPosition = 0
    private var videoWidth = 0
    private var videoHeight = 0
    private var state = VideoPlaybackState.RELEASED

    val positionMs: Int
        get() = if (prepared) runCatching { player?.currentPosition ?: savedPosition }.getOrDefault(savedPosition) else savedPosition

    fun attach(textureView: TextureView) {
        if (view !== textureView) {
            view?.surfaceTextureListener = null
            release()
            texture = null
        }
        view = textureView
        textureView.isOpaque = false
        textureView.surfaceTextureListener = this
        if (textureView.isAvailable) {
            texture = textureView.surfaceTexture
            startIfReady()
        }
    }

    fun detach(textureView: TextureView) {
        if (view !== textureView) return
        textureView.surfaceTextureListener = null
        texture = null
        view = null
        release()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) release()
        else {
            if (state == VideoPlaybackState.ERROR) publish(VideoPlaybackState.RELEASED)
            startIfReady()
        }
    }

    private fun startIfReady() {
        if (!active || player != null || state == VideoPlaybackState.ERROR) return
        val available = texture ?: return
        try {
            output = Surface(available)
            val media = MediaPlayer()
            player = media
            publish(VideoPlaybackState.PREPARING)
            media.setSurface(output)
            media.setDataSource(path)
            media.isLooping = true
            media.setVolume(0f, 0f)
            media.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width; videoHeight = height; updateCrop()
            }
            media.setOnErrorListener { _, _, _ -> fail(); true }
            media.setOnPreparedListener {
                if (player === it && active) {
                    prepared = true
                    videoWidth = it.videoWidth; videoHeight = it.videoHeight; updateCrop()
                    val bookmark = savedPosition.coerceIn(0, (it.duration - 1).coerceAtLeast(0))
                    if (bookmark > 0) {
                        it.setOnSeekCompleteListener { sought ->
                            if (player === sought && active) start(sought)
                        }
                        it.seekTo(bookmark.toLong(), MediaPlayer.SEEK_CLOSEST)
                    } else start(it)
                }
            }
            media.prepareAsync()
        } catch (_: Exception) { fail() }
    }

    private fun start(media: MediaPlayer) {
        try {
            media.setOnSeekCompleteListener(null)
            media.start()
            publish(VideoPlaybackState.PLAYING)
            Log.i("GlassWallpaper", "Video started at ${positionMs}ms")
        } catch (_: Exception) { fail() }
    }

    private fun fail() {
        publish(VideoPlaybackState.ERROR)
        release()
        Log.w("GlassWallpaper", "Video unavailable; using cached still")
    }

    private fun release() {
        savedPosition = positionMs
        prepared = false
        player?.let {
            it.setOnPreparedListener(null)
            it.setOnErrorListener(null)
            it.setOnSeekCompleteListener(null)
            it.setOnVideoSizeChangedListener(null)
            it.release()
            Log.i("GlassWallpaper", "Video released at ${savedPosition}ms")
        }
        player = null
        output?.release()
        output = null
        if (state != VideoPlaybackState.ERROR) publish(VideoPlaybackState.RELEASED)
    }

    fun dispose() {
        active = false
        release()
        view?.surfaceTextureListener = null
        view = null
        texture = null
    }

    private fun publish(value: VideoPlaybackState) {
        state = value
        onState(value)
    }

    private fun updateCrop() {
        val target = view ?: return
        val crop = videoCenterCrop(target.width, target.height, videoWidth, videoHeight)
        target.setTransform(Matrix().apply { setScale(crop.scaleX, crop.scaleY, target.width / 2f, target.height / 2f) })
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        texture = surface
        updateCrop()
        startIfReady()
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        texture = null
        release()
        return true
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = updateCrop()
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}
