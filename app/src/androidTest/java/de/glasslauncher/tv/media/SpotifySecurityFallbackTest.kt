package de.glasslauncher.tv.media

import android.content.ComponentName
import android.media.session.MediaSessionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotifySecurityFallbackTest {
    @Test fun missingSessionAccessKeepsAdapterDisconnectedAndControlsDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val denied = try {
            manager.getActiveSessions(ComponentName(context, GlassMediaListener::class.java))
            false
        } catch (_: SecurityException) { true }
        // This test never changes the device's permission or notification-access configuration.
        assumeTrue("Run on a device without Glass TV notification/session access", denied)
        instrumentation.runOnMainSync {
            val adapter = SpotifySessionAdapter(context)
            try {
                adapter.start()
                assertFalse(adapter.state.value.connected)
                assertNull(adapter.state.value.artwork)
                assertFalse(adapter.playPause())
                assertFalse(adapter.previous())
                assertFalse(adapter.next())
                assertTrue(adapter.state.value.status.contains("nicht freigegeben"))
            } finally { adapter.close() }
        }
    }
}
