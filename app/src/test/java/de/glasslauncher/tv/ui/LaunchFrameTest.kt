package de.glasslauncher.tv.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class LaunchFrameTest {
    private val source = Rect(100f, 300f, 244f, 381f)
    private val viewport = Size(960f, 540f)

    @Test fun transparentStartPreservesSourceAndNeverHidesBackground() {
        val frame = launchFrame(source, viewport, 0f, 1f)
        assertEquals(source, frame.bounds)
        assertEquals(0f, frame.opacity)
        assertNull(frame.opaqueInterior)
        assertNull(launchFrame(source, viewport, .1f, 1f).opaqueInterior)
    }

    @Test fun completedHandoffOccludesWholeViewport() {
        val frame = launchFrame(source, viewport, 1f, 1f)
        assertEquals(Rect(0f, 0f, 960f, 540f), frame.bounds)
        assertEquals(frame.bounds, frame.opaqueInterior)
        assertEquals(0f, frame.cornerRadius)
        assertEquals(270f, frame.iconCenterY)
    }

    @Test fun expandingOpaqueAreaNeverErasesRoundedCorners() {
        val frame = launchFrame(source, viewport, .5f, 1f)
        assertNotNull(frame.opaqueInterior)
        val interior = requireNotNull(frame.opaqueInterior)
        assertEquals(1f, frame.opacity)
        assertTrue(interior.left >= frame.bounds.left + frame.cornerRadius)
        assertTrue(interior.top >= frame.bounds.top + frame.cornerRadius)
        assertTrue(interior.right <= frame.bounds.right - frame.cornerRadius)
        assertTrue(interior.bottom <= frame.bounds.bottom - frame.cornerRadius)
        assertTrue(interior.width > 144f)
    }
}
