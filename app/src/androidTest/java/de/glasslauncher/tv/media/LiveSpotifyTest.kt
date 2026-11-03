package de.glasslauncher.tv.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.data.ConfigRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit device opt-in. Never grants access, reads notifications or logs metadata values. */
class LiveSpotifyTest {
    @Test fun warmForegroundReconnectsWithoutAnotherPreDraw() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSpotify")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assertTrue("Enable the existing albumcover mode for the live acceptance test",ConfigRepository(context).load().musicMode)
        val store=androidx.lifecycle.ViewModelStore()
        lateinit var model:de.glasslauncher.tv.LauncherViewModel
        instrumentation.runOnMainSync {
            model=androidx.lifecycle.ViewModelProvider(store,androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application))[de.glasslauncher.tv.LauncherViewModel::class.java]
        }
        try {
            kotlinx.coroutines.withTimeout(5000) { androidx.compose.runtime.snapshotFlow { model.ready }.first { it } }
            instrumentation.runOnMainSync { model.foreground(); model.startOptionalMedia() }
            kotlinx.coroutines.withTimeout(5000) { model.music.first { it.connected } }
            instrumentation.runOnMainSync { model.background() }
            assertFalse(model.music.value.connected)
            instrumentation.runOnMainSync { model.foreground() }
            val resumed=kotlinx.coroutines.withTimeoutOrNull(2500) { model.music.first { it.connected } }
            assertNotNull("Warm foreground must reconnect without a fresh pre-draw callback",resumed)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun currentDeviceChainCanReadSpotify() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSpotify")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val config=ConfigRepository(context).load()
        MediaTrace.event("probe.config", "mode=${config.musicMode}")
        val all=context.getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(context,GlassMediaListener::class.java))
        val spotify=all.filter { it.packageName=="com.spotify.tv.android" }
        assertTrue("Real Spotify TV session must exist",spotify.isNotEmpty())
        val metadata=spotify.first().metadata
        MediaTrace.event("probe.metadata", "state=${spotify.first().playbackState?.state} title=${!metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()} artist=${!metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrBlank()}")
        for(key in listOf(MediaMetadata.METADATA_KEY_ALBUM_ART,MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_DISPLAY_ICON))
            MediaTrace.event("probe.art.bitmap", "key=$key present=${runCatching { metadata?.getBitmap(key)!=null }.getOrDefault(false)}")
        for(key in listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
            val uri=metadata?.getString(key)?.let(android.net.Uri::parse)
            MediaTrace.event("probe.art.uri", "key=$key present=${uri!=null} scheme=${uri?.scheme} spotifyCdn=${uri?.host=="i.scdn.co"}")
        }
        lateinit var adapter:SpotifySessionAdapter
        instrumentation.runOnMainSync { adapter=SpotifySessionAdapter(context); adapter.start() }
        try { assertTrue("Adapter must select the genuine Spotify session",adapter.state.value.connected) }
        finally { instrumentation.runOnMainSync { adapter.close() } }
    }
}
