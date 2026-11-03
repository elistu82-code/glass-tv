package de.glasslauncher.tv.media

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.MainActivity
import de.glasslauncher.tv.LauncherViewModel
import de.glasslauncher.tv.testing.AwakeDeviceRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Spotify only; explicit opt-in. No shell transport proxy, fake discovery or permission grants. */
@OptIn(ExperimentalTestApi::class)
class LiveSpotifyUiTest {
    @get:Rule(order=0) val awake=AwakeDeviceRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var model:LauncherViewModel
    @Before fun requireExplicitLiveRun() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSpotify")=="true")
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model=ViewModelProvider(it)[LauncherViewModel::class.java]
        }
    }
    private fun key(key:Key) { compose.onRoot().performKeyInput { pressKey(key) } }
    private fun await(message:String,predicate:()->Boolean) {
        try { compose.waitUntil(timeoutMillis=15000,predicate) }
        catch (_:ComposeTimeoutException) { throw AssertionError(message) }
        compose.waitForIdle()
    }
    private fun screenshot(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir,"spotify-live-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun controller():MediaController {
        val context=compose.activity
        return context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context,GlassMediaListener::class.java)).first { it.packageName=="com.spotify.tv.android" }
    }
    private fun focusPlayFromDock() {
        key(Key.DirectionUp);compose.onNodeWithTag("music:1").assertIsFocused()
    }
    @Test fun genuineSpotifyCommandsAndArtworkReachDashboard() {
        await("Granted real Spotify session must reach the launcher") { model.music.value.connected }
        assertTrue(model.config.musicMode)
        val real=controller()
        val initialPlaying=model.music.value.isPlaying
        val originalVisualizer=model.config.simulatedVisualizer
        val savedWallpaper=model.config.wallpaperPath
        val savedTheme=model.config.theme
        try {
            compose.runOnIdle { model.update { it.copy(simulatedVisualizer=true) } }
            await("Enable only the simulated visualizer for this live probe") { model.config.simulatedVisualizer }
            await("Real Spotify must supply title, artist and embedded cover") {
                val s=model.music.value;s.title.isNotBlank() && s.artist.isNotBlank() && s.artwork!=null
            }
            compose.onNodeWithTag("music-album-cover").assertIsDisplayed()
            compose.onNodeWithTag("music-title").assertTextEquals(model.music.value.title)
            compose.onNodeWithTag("music-artist").assertTextEquals(model.music.value.artist)
            assertTrue("Live Spotify duration is needed by the progress bar",model.music.value.durationMs>0)
            compose.onNodeWithTag("music-progress").assertExists()
            focusPlayFromDock()
            if(initialPlaying) { key(Key.DirectionCenter); await("UI Pause command must be observed") { !model.music.value.isPlaying } }
            val first=model.music.value
            key(Key.DirectionCenter); await("UI Play command must be observed") { model.music.value.isPlaying }
            compose.onNodeWithTag("spotify-visualizer").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Simulation läuft"))
            screenshot("playing")
            key(Key.DirectionCenter); await("UI Pause command must be observed") { !model.music.value.isPlaying && model.music.value.connected }
            assertNotNull("Paused cover must stay visible",model.music.value.artwork)
            assertTrue("Same-track pause must retain artwork",requireNotNull(first.artwork).sameAs(requireNotNull(model.music.value.artwork)))
            compose.onNodeWithTag("spotify-visualizer").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Simulation pausiert"))
            screenshot("paused")
            key(Key.DirectionRight); compose.onNodeWithTag("music:2").assertIsFocused();key(Key.DirectionCenter)
            await("UI Next must change real track metadata") { model.music.value.title!=first.title || model.music.value.artist!=first.artist }
            await("New real track needs an album cover") { model.music.value.artwork!=null }
            screenshot("next")
            val second=model.music.value
            key(Key.DirectionLeft);key(Key.DirectionLeft);compose.onNodeWithTag("music:0").assertIsFocused();key(Key.DirectionCenter)
            // Spotify may first seek to the beginning when more than a few seconds into a track.
            compose.waitUntil(timeoutMillis=15000) {
                model.music.value.title!=second.title || model.music.value.artist!=second.artist || (controller().playbackState?.position ?: Long.MAX_VALUE)<1500
            }
            if(model.music.value.title==second.title && model.music.value.artist==second.artist) key(Key.DirectionCenter)
            await("UI Previous must change real track metadata") { model.music.value.title!=second.title || model.music.value.artist!=second.artist }
            screenshot("previous")
            assertTrue("Real session must advertise STOP for the end-of-playback probe",(real.playbackState?.actions ?: 0) and PlaybackState.ACTION_STOP!=0L)
            compose.runOnIdle { real.transportControls.play() }
            await("Start before characterizing the real STOP response") { model.music.value.isPlaying }
            compose.runOnIdle { real.transportControls.stop() }
            await("Real Spotify STOP must produce an observed playback response") {
                real.playbackState?.state in listOf(PlaybackState.STATE_PAUSED,PlaybackState.STATE_STOPPED,PlaybackState.STATE_NONE,PlaybackState.STATE_ERROR)
            }
            val terminal=real.playbackState?.state!=PlaybackState.STATE_PAUSED
            if (terminal) {
                await("Terminal session must restore wallpaper and remove active controls") { !model.music.value.connected && model.music.value.artwork==null }
                compose.onNodeWithTag("music-controls").assertDoesNotExist()
                compose.onNodeWithTag("spotify-visualizer").assertDoesNotExist()
                screenshot("stopped")
            } else {
                await("Reported PAUSED must retain the cover and allow resume") { model.music.value.connected && !model.music.value.isPlaying && model.music.value.artwork!=null }
                compose.onNodeWithTag("music:1").assertIsEnabled()
                screenshot("stop-reports-paused")
            }
            assertEquals(savedWallpaper,model.config.wallpaperPath);assertEquals(savedTheme,model.config.theme)
            MediaTrace.event("live.stop.observed","state=${real.playbackState?.state} terminal=$terminal")
            File(compose.activity.cacheDir,"spotify-live-result.json").writeText(com.google.gson.JsonObject().apply {
                addProperty("package","com.spotify.tv.android");addProperty("play",true);addProperty("pause",true)
                addProperty("next",true);addProperty("previous",true);addProperty("cover",true)
                addProperty("coverWidth",first.artwork?.width);addProperty("coverHeight",first.artwork?.height)
                addProperty("stopState",real.playbackState?.state);addProperty("terminal",terminal)
            }.toString())
            MediaTrace.event("live.ui.verified","play=true pause=true next=true previous=true stopObserved=true cover=true")
        } finally {
            compose.runOnIdle { model.update { it.copy(simulatedVisualizer=originalVisualizer) } }
            await("Restore saved visualizer preference") { model.config.simulatedVisualizer==originalVisualizer }
            // Restore a usable session and the original play/pause state after the transport probe.
            compose.runOnIdle { real.transportControls.play() }
            await("Spotify must return after the STOP probe") { model.music.value.connected && model.music.value.isPlaying }
            if(!initialPlaying) { compose.runOnIdle { model.playPause() };await("Restore original paused state") { !model.music.value.isPlaying } }
        }
    }
}
