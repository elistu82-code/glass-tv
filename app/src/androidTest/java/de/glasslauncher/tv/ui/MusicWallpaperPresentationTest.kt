package de.glasslauncher.tv.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.core.LauncherCallbacks
import de.glasslauncher.tv.data.AppRepository
import de.glasslauncher.tv.data.ConfigCodec
import de.glasslauncher.tv.media.SpotifyState
import de.glasslauncher.tv.testing.AwakeDeviceRule
import de.glasslauncher.tv.wallpaper.WallpaperEngine
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real TV rendering with explicit controlled music state; no Spotify/session impersonation. */
@OptIn(ExperimentalTestApi::class)
class MusicWallpaperPresentationTest {
    @get:Rule(order=0) val awake=AwakeDeviceRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun selectedWallpaperSeparateCoverAndWavyProgressRenderTogether() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val config=ConfigCodec.decode(File(context.filesDir,"launcher-config.json").readText()).copy(musicMode=true,uiSounds=false)
        val apps=AppRepository(context).discover()
        val art=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(38,50,88))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(154,195,214) }
            Canvas(this).apply { drawCircle(128f,112f,67f,paint);paint.color=android.graphics.Color.WHITE;paint.textSize=22f;drawText("UI-PROBE",74f,224f,paint) }
        }
        val music=SpotifyState(connected=true,isPlaying=true,title="Kontrollierte UI-Probe",artist="Kein Spotify-Live-Nachweis",artwork=art,canPrevious=true,canPlayPause=true,canNext=true,positionMs=74000,durationMs=240000,positionUpdatedAtMs=android.os.SystemClock.elapsedRealtime(),playbackSpeed=1f)
        val callbacks=LauncherCallbacks(onConfigChange={},onLaunch={},onAppInfo={},onUninstall={},onImportWallpaper={},onOpenNetwork={},onStockHome={},onExportConfig={},onImportConfig={})
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val feedback=UiSoundFeedback(android.os.SystemClock::uptimeMillis) { }
        compose.setContent { CompositionLocalProvider(LocalUiSounds provides feedback) {
            LauncherScreen(apps,config,callbacks,music=music) { full -> WallpaperEngine(config,full,musicActive=true) }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("wallpaper-still").fetchSemanticsNodes().size==1 }
        compose.onNodeWithTag("music-album-cover").assertIsDisplayed()
        compose.onNodeWithTag("music-progress").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("music:1").assertIsFocused()
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir,"music-064-controlled.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        Unit
    }
}
