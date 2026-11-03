package de.glasslauncher.tv.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real local Android session; only session discovery is supplied, never Spotify or system access. */
@RunWith(AndroidJUnit4::class)
class ControlledSessionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(adapter: SpotifySessionAdapter, predicate: (SpotifyState) -> Boolean): SpotifyState =
        runBlocking { withTimeout(5_000) { adapter.state.first(predicate) } }

    private class Provider(context: Context) : AutoCloseable {
        val session = MediaSession(context, "Glass TV controlled test")
        val commands = LinkedBlockingQueue<String>()
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT
        val source = object : SpotifySessions {
            override fun register(onChanged: () -> Unit) = Unit
            override fun active(): List<MediaController> = listOf(session.controller)
            override fun unregister() = Unit
        }
        init {
            session.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { playback(PlaybackState.STATE_PLAYING); commands.add("play") }
                override fun onPause() { playback(PlaybackState.STATE_PAUSED); commands.add("pause") }
                override fun onSkipToNext() { metadata("Second", Color.BLUE); commands.add("next") }
                override fun onSkipToPrevious() { metadata("First", Color.RED); commands.add("previous") }
            }, Handler(Looper.getMainLooper()))
            metadata("First", Color.RED)
            playback(PlaybackState.STATE_PLAYING)
            session.isActive = true
        }
        fun metadata(title: String, color: Int) {
            val art = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            session.setMetadata(MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,200000).putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Controlled artist")
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build())
        }
        fun playback(state: Int) { session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(state, 0L, 1f).build()) }
        override fun close() = session.release()
    }

    @Test fun playbackTicksReuseUnchangedMetadataAndArtwork() {
        lateinit var provider:Provider
        lateinit var adapter:SpotifySessionAdapter
        onMain { provider=Provider(instrumentation.targetContext); adapter=SpotifySessionAdapter(provider.source); adapter.start() }
        try {
            val first=await(adapter) { it.connected && it.isPlaying }
            val delivered=CountDownLatch(1)
            val observer=object:MediaController.Callback() { override fun onPlaybackStateChanged(state:PlaybackState?) { delivered.countDown() } }
            onMain { provider.session.controller.registerCallback(observer,Handler(Looper.getMainLooper())); provider.playback(PlaybackState.STATE_PLAYING) }
            try { assertTrue(delivered.await(5,TimeUnit.SECONDS)); instrumentation.waitForIdleSync() }
            finally { onMain { provider.session.controller.unregisterCallback(observer) } }
            assertSame("Position updates must not re-fetch and re-publish the same cover",first.artwork,adapter.state.value.artwork)
            assertEquals(first.artworkKey,adapter.state.value.artworkKey)
        } finally { onMain { adapter.close();provider.close() } }
    }

    @Test fun realSessionPublishesMetadataRetainsPausedCoverAndClearsStoppedOrDestroyedSession() {
        lateinit var provider: Provider
        lateinit var adapter: SpotifySessionAdapter
        onMain { provider = Provider(instrumentation.targetContext); adapter = SpotifySessionAdapter(provider.source); adapter.start() }
        try {
            val first = await(adapter) { it.connected && it.title == "First" && it.isPlaying }
            assertEquals("Controlled artist", first.artist)
            assertEquals(Color.RED, requireNotNull(first.artwork).getPixel(0, 0))
            onMain { provider.playback(PlaybackState.STATE_PAUSED) }
            await(adapter) { it.connected && !it.isPlaying }
            val cleared = CountDownLatch(1)
            val observer = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) { if (metadata == null) cleared.countDown() }
            }
            onMain { provider.session.controller.registerCallback(observer, Handler(Looper.getMainLooper())); provider.session.setMetadata(null) }
            try {
                assertTrue("Metadata-clear callback missing", cleared.await(5, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
            } finally { onMain { provider.session.controller.unregisterCallback(observer) } }
            val paused = adapter.state.value
            assertEquals("First", paused.title)
            assertEquals(200000L,paused.durationMs)
            assertEquals(Color.RED, requireNotNull(paused.artwork).getPixel(0, 0))
            onMain { provider.playback(PlaybackState.STATE_STOPPED) }
            val stopped = await(adapter) { !it.connected }
            assertNull(stopped.artwork)
            assertFalse(stopped.canPlayPause)
            onMain { provider.metadata("Second", Color.BLUE); provider.playback(PlaybackState.STATE_PLAYING) }
            val second = await(adapter) { it.connected && it.title == "Second" }
            assertEquals(Color.BLUE, requireNotNull(second.artwork).getPixel(0, 0))
            onMain { provider.close() }
            assertNull(await(adapter) { !it.connected }.artwork)
        } finally { onMain { adapter.close(); provider.close() } }
    }

    @Test fun transportCommandsReachRealSessionAndRespectAdvertisedActions() {
        lateinit var provider: Provider
        lateinit var adapter: SpotifySessionAdapter
        onMain { provider = Provider(instrumentation.targetContext); adapter = SpotifySessionAdapter(provider.source); adapter.start() }
        try {
            await(adapter) { it.connected && it.canNext && it.canPrevious && it.canPlayPause }
            onMain { assertTrue(adapter.next()) }
            assertEquals("next", provider.commands.poll(5, TimeUnit.SECONDS))
            await(adapter) { it.title == "Second" }
            onMain { assertTrue(adapter.previous()) }
            assertEquals("previous", provider.commands.poll(5, TimeUnit.SECONDS))
            await(adapter) { it.title == "First" }
            onMain { assertTrue(adapter.playPause()) }
            assertEquals("pause", provider.commands.poll(5, TimeUnit.SECONDS))
            await(adapter) { !it.isPlaying }
            onMain { assertTrue(adapter.playPause()) }
            assertEquals("play", provider.commands.poll(5, TimeUnit.SECONDS))
            await(adapter) { it.isPlaying }
            onMain { provider.actions = PlaybackState.ACTION_PLAY_PAUSE; provider.playback(PlaybackState.STATE_PLAYING) }
            await(adapter) { it.connected && !it.canPrevious && !it.canNext }
            onMain { assertFalse(adapter.previous()); assertFalse(adapter.next()); assertTrue(adapter.playPause()) }
            assertEquals("pause", provider.commands.poll(5, TimeUnit.SECONDS))
            await(adapter) { !it.isPlaying }
        } finally { onMain { adapter.close(); provider.close() } }
    }
}
