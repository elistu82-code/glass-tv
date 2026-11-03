package de.glasslauncher.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiSoundRouteTest {
    @Test fun mutedFireOsSystemEffectsUseAudibleMediaWithoutChangingVolumes() {
        assertEquals(UiSoundRoute.MEDIA,uiSoundRoute(true,false,true))
    }
    @Test fun availableNativeEffectsAreExclusiveAndRespectTheGlobalEffectsSwitch() {
        assertEquals(UiSoundRoute.NATIVE,uiSoundRoute(true,true,true))
        assertEquals(UiSoundRoute.NATIVE,uiSoundRoute(true,true,false))
        assertEquals(UiSoundRoute.SILENT,uiSoundRoute(false,true,true))
        assertEquals(UiSoundRoute.SILENT,uiSoundRoute(false,false,true))
    }
    @Test fun bothMutedChannelsRemainSilent() {
        assertEquals(UiSoundRoute.SILENT,uiSoundRoute(true,false,false))
    }
}
