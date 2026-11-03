package de.glasslauncher.tv.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.core.*
import de.glasslauncher.tv.data.AppRepository
import de.glasslauncher.tv.media.GlassMediaListener
import de.glasslauncher.tv.media.SpotifyState
import de.glasslauncher.tv.testing.AwakeDeviceRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real installed-app launch, controlled music UI. Never sends Spotify transport/transfer commands. */
@OptIn(ExperimentalTestApi::class)
class SpotifyShortcutTest {
    @get:Rule(order=0) val awake=AwakeDeviceRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun missingOrSelfPackageFailsWithoutLaunchingAnything() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=AppRepository(context)
        assertFalse(repository.launchPackage("de.glasslauncher.nonexistent.spotify.fixture"))
        assertFalse(repository.launchPackage(context.packageName))
    }
    @Test fun coverLaunchesTheInstalledSpotifyAppWithoutPlaybackExtrasAndReturnsToCover() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveShortcut")=="true")
        val context=instrumentation.targetContext
        val pm=context.packageManager
        assumeTrue(pm.getLeanbackLaunchIntentForPackage("com.spotify.tv.android")!=null)
        val manager=context.getSystemService(MediaSessionManager::class.java)
        fun playback()=runCatching { manager.getActiveSessions(ComponentName(context,GlassMediaListener::class.java))
            .firstOrNull { it.packageName=="com.spotify.tv.android" }?.playbackState?.state }.getOrNull()
        val before=playback()
        var captured:Intent?=null
        val monitor=object:Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.component?.packageName=="com.spotify.tv.android") captured=Intent(intent)
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        var launched:Boolean?=null
        val repository=AppRepository(context)
        val callbacks=LauncherCallbacks(onConfigChange={},onLaunch={},onAppInfo={},onUninstall={},onImportWallpaper={},onOpenNetwork={},onStockHome={},onExportConfig={},onImportConfig={},
            onOpenMusic={compose.activity.lifecycleScope.launch { launched=repository.launchPackage("com.spotify.tv.android") }})
        val feedback=UiSoundFeedback(android.os.SystemClock::uptimeMillis) { }
        try {
            compose.setContent { CompositionLocalProvider(LocalUiSounds provides feedback) {
                LauncherScreen(listOf(InstalledApp("a","fixture.a","Fixture")),LauncherConfig(musicMode=true),callbacks,
                    music=SpotifyState(connected=true,title="Controlled shortcut probe",canPrevious=true,canPlayPause=true,canNext=true)) { }
            } }
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp);repeat(2) { pressKey(Key.DirectionRight) } }
            compose.onNodeWithTag("music-album-cover").assertIsFocused()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(10000) { launched!=null && captured!=null }
            assertTrue(launched==true)
            assertEquals(Intent.ACTION_MAIN,captured?.action)
            assertEquals("com.spotify.tv.android",captured?.component?.packageName)
            assertTrue(captured?.extras==null || captured?.extras?.isEmpty==true)
            assertEquals(0,(captured?.flags ?: 0) and Intent.FLAG_ACTIVITY_CLEAR_TASK)
            // Verify the actual system foreground, not just a successful Context.startActivity call.
            fun top():String {
                val descriptor=instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            }
            compose.waitUntil(10000) { top().lineSequence().any { "mResumedActivity:" in it && "com.spotify.tv.android/" in it } }
            val after=playback()
            if(before in listOf(2,3)) assertEquals("Opening an existing session must preserve play/pause",before,after)
            compose.activity.runOnUiThread { compose.activity.startActivity(Intent(compose.activity,compose.activity.javaClass)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)) }
            compose.waitUntil(10000) { top().lineSequence().any { "mResumedActivity:" in it && "de.glasslauncher.tv/" in it } }
            compose.waitForIdle()
            compose.onNodeWithTag("music-album-cover").assertIsFocused()
            File(context.cacheDir,"spotify-shortcut-result.json").writeText("""{"realSpotifyActivityOpened":true,"plainMainIntent":true,"noPlaybackExtras":true,"coverFocusRestored":true,"beforeState":$before,"afterState":$after}""")
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
