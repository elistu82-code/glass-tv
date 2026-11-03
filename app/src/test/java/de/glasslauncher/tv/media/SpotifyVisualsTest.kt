package de.glasslauncher.tv.media

import de.glasslauncher.tv.core.LauncherConfig
import org.junit.Assert.*
import org.junit.Test

class SpotifyVisualsTest {
    private val optedIn=LauncherConfig(musicMode=true,simulatedVisualizer=true)
    @Test fun onlyConnectedPlayingOptedInSessionAnimates() {
        assertEquals(SpotifyVisuals(true,true),spotifyVisuals(optedIn,SpotifyState(connected=true,isPlaying=true)))
        assertEquals(SpotifyVisuals(true,false),spotifyVisuals(optedIn,SpotifyState(connected=true,isPlaying=false)))
        assertEquals(SpotifyVisuals(false,false),spotifyVisuals(optedIn,SpotifyState()))
    }
    @Test fun optOutAndReducedMotionAreRespected() {
        val playing=SpotifyState(connected=true,isPlaying=true)
        assertEquals(SpotifyVisuals(false,false),spotifyVisuals(optedIn.copy(musicMode=false),playing))
        assertEquals(SpotifyVisuals(false,false),spotifyVisuals(optedIn.copy(simulatedVisualizer=false),playing))
        assertEquals(SpotifyVisuals(true,false),spotifyVisuals(optedIn.copy(reducedMotion=true),playing))
    }
}
