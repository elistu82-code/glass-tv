package de.glasslauncher.tv.wallpaper

import org.junit.Assert.*
import org.junit.Test

class VideoGeometryTest {
    @Test fun matchingAspectRatioDoesNotScaleTexture() {
        assertEquals(VideoCrop(1f, 1f), videoCenterCrop(1920, 1080, 320, 180))
    }

    @Test fun landscapeVideoCropsHorizontallyInSquareViewWithoutStretching() {
        val crop = videoCenterCrop(100, 100, 200, 100)
        assertEquals(2f, crop.scaleX, .0001f)
        assertEquals(1f, crop.scaleY, .0001f)
    }

    @Test fun portraitVideoCropsVerticallyInLandscapeViewWithoutStretching() {
        val crop = videoCenterCrop(200, 100, 100, 200)
        assertEquals(1f, crop.scaleX, .0001f)
        assertEquals(4f, crop.scaleY, .0001f)
    }

    @Test fun unavailableDimensionsKeepIdentityUntilVideoIsPrepared() {
        assertEquals(VideoCrop(1f, 1f), videoCenterCrop(0, 1080, 320, 180))
        assertEquals(VideoCrop(1f, 1f), videoCenterCrop(1920, 1080, 0, 0))
    }
}
