package de.glasslauncher.tv.wallpaper

import org.junit.Assert.*
import org.junit.Test

class WallpaperPolicyTest {
    @Test fun videoOnlyRunsOnResumedHome() {
        assertTrue(WallpaperPolicy.shouldPlay(resumed = true, fullSettings = false, video = true))
        assertFalse(WallpaperPolicy.shouldPlay(resumed = false, fullSettings = false, video = true))
        assertFalse(WallpaperPolicy.shouldPlay(resumed = true, fullSettings = true, video = true))
        assertFalse(WallpaperPolicy.shouldPlay(resumed = true, fullSettings = false, video = false))
    }
}
