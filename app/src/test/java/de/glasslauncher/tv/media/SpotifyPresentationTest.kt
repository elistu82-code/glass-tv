package de.glasslauncher.tv.media

import org.junit.Assert.*
import org.junit.Test

class SpotifyPresentationTest {
    @Test fun playingProgressInterpolatesFromElapsedRealtimeAndPlaybackSpeed() {
        assertEquals(.25f,requireNotNull(songProgress(40000,200000,10000,1f,true,20000)),.0001f)
        assertEquals(.30f,requireNotNull(songProgress(40000,200000,10000,2f,true,20000)),.0001f)
    }
    @Test fun pausedProgressFreezesAndUnknownDurationOrPositionStaysUnknown() {
        assertEquals(.2f,requireNotNull(songProgress(40000,200000,10000,1f,false,999999)),.0001f)
        assertNull(songProgress(100,0,10000,1f,true,20000))
        assertNull(songProgress(-1,200000,10000,1f,true,20000))
    }
    @Test fun seeksAndBadClocksNeverDrawOutsideTrackOrProduceNan() {
        assertEquals(1f,requireNotNull(songProgress(190000,200000,10000,1f,true,999999)),.0001f)
        assertEquals(.2f,requireNotNull(songProgress(40000,200000,20000,1f,true,10000)),.0001f)
        assertEquals(.2f,requireNotNull(songProgress(40000,200000,10000,Float.NaN,true,20000)),.0001f)
    }
    @Test fun coverQualityRejectsMissingTinyRecycledAndExtremeBannerImages() {
        assertTrue(suitableMusicArtwork(640,640,false))
        assertTrue(suitableMusicArtwork(128,128,false))
        assertFalse(suitableMusicArtwork(16,16,false))
        assertFalse(suitableMusicArtwork(640,640,true))
        assertFalse(suitableMusicArtwork(1024,100,false))
        assertFalse(suitableMusicArtwork(0,0,false))
    }
}
