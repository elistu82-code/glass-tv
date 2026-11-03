package de.glasslauncher.tv.media

import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class SpotifyStateTest {
    @Test fun pausedCanResumeOnlyWhenPlayOrToggleAdvertised() {
        assertTrue(sessionCapabilities(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY).playPause)
        assertTrue(sessionCapabilities(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY_PAUSE).playPause)
        assertFalse(sessionCapabilities(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PAUSE).playPause)
    }

    @Test fun playingCanPauseOnlyWhenPauseOrToggleAdvertised() {
        val playing = sessionCapabilities(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE)
        assertTrue(playing.active)
        assertTrue(playing.playing)
        assertTrue(playing.playPause)
        assertFalse(sessionCapabilities(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY).playPause)
    }

    @Test fun stoppedNoneAndErrorCannotRetainActiveControls() {
        val all = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        listOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR).forEach {
            assertEquals(SessionCapabilities(false, false, false, false, false), sessionCapabilities(it, all))
        }
    }

    @Test fun skipCapabilitiesAreIndependentAndAbsentActionsDoNotInventControls() {
        val next = sessionCapabilities(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_SKIP_TO_NEXT)
        assertTrue(next.active)
        assertTrue(next.next)
        assertFalse(next.previous)
        assertFalse(next.playPause)
        val previous = sessionCapabilities(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        assertTrue(previous.previous)
        assertFalse(previous.next)
        assertFalse(sessionCapabilities(PlaybackState.STATE_PLAYING, 0).playPause)
    }

    @Test fun packageAllowlistIsExactIncludingOptionalAmazonBridge() {
        assertTrue(isSpotifyPackage("com.spotify.tv.android"))
        assertTrue(isSpotifyPackage("com.spotify.music"))
        assertTrue(isSpotifyPackage("com.amazon.spotify.mediabrowserservice"))
        assertFalse(isSpotifyPackage("com.spotify.tv.android.fake"))
        assertFalse(isSpotifyPackage("com.google.android.youtube.tv"))
    }
}
