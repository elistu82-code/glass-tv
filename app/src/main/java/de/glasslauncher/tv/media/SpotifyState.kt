package de.glasslauncher.tv.media

import android.graphics.Bitmap
import android.media.session.PlaybackState

data class SpotifyState(
    val title: String = "",
    val artist: String = "",
    val artwork: Bitmap? = null,
    val artworkKey: String? = null,
    val isPlaying: Boolean = false,
    val connected: Boolean = false,
    val canPlayPause: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val status: String = "Spotify nicht verbunden",
    val positionMs: Long = -1,
    val durationMs: Long = 0,
    val positionUpdatedAtMs: Long = 0,
    val playbackSpeed: Float = 0f,
)

internal data class SessionCapabilities(
    val active: Boolean,
    val playing: Boolean,
    val playPause: Boolean,
    val previous: Boolean,
    val next: Boolean,
)

/** Pure mapping: capabilities depend on the advertised actions, never package installation. */
internal fun sessionCapabilities(playback: Int, actions: Long): SessionCapabilities {
    val active = playback in setOf(
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING, PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
    )
    val playing = playback == PlaybackState.STATE_PLAYING
    fun has(action: Long) = actions and action != 0L
    return SessionCapabilities(
        active = active,
        playing = active && playing,
        playPause = active && (has(PlaybackState.ACTION_PLAY_PAUSE) ||
            has(if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY)),
        previous = active && has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        next = active && has(PlaybackState.ACTION_SKIP_TO_NEXT),
    )
}

internal fun isSpotifyPackage(packageName: String) = packageName in setOf(
    "com.spotify.tv.android", "com.spotify.music", "com.amazon.spotify.mediabrowserservice",
)
