package de.glasslauncher.tv.wallpaper

import android.view.TextureView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.testing.AwakeDeviceRule
import org.junit.Before
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VideoWallpaperTest {
    @get:Rule(order = 0) val awake = AwakeDeviceRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestActivityAwake() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    private fun fixture(): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return File(instrumentation.targetContext.cacheDir, "wallpaper-test.mp4").also { target ->
            instrumentation.context.assets.open("wallpaper-test.mp4").use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
    }

    @Test fun realDecoderReleasesAndResumesFromSavedPosition() {
        val clip = fixture()
        val state = AtomicReference(VideoPlaybackState.RELEASED)
        val controller = VideoPlaybackController(clip.path, state::set)
        try {
            compose.setContent {
                AndroidView(factory = { context -> TextureView(context).also(controller::attach) }, modifier = Modifier.fillMaxSize())
            }
            compose.runOnIdle { controller.setActive(true) }
            compose.waitUntil(15_000) { state.get() == VideoPlaybackState.PLAYING }
            compose.waitUntil(10_000) {
                var advanced = false
                compose.runOnIdle { advanced = controller.positionMs >= 350 }
                advanced
            }
            var bookmark = 0
            compose.runOnIdle {
                controller.setActive(false)
                bookmark = controller.positionMs
                assertEquals(VideoPlaybackState.RELEASED, state.get())
            }
            assertTrue("Released player must retain its bookmark", bookmark >= 350)
            compose.runOnIdle { controller.setActive(true) }
            compose.waitUntil(15_000) { state.get() == VideoPlaybackState.PLAYING }
            compose.runOnIdle { assertTrue(controller.positionMs >= bookmark - 100) }
        } finally {
            compose.runOnIdle { controller.dispose() }
            clip.delete()
        }
    }

    @Test fun fullSettingsRemovesVideoAndReturnsToPlaybackOnClose() {
        val clip = fixture()
        val fullSettings = mutableStateOf(false)
        try {
            compose.setContent { WallpaperEngine(LauncherConfig(wallpaperPath = clip.path, wallpaperVideo = true), fullSettings.value) }
            compose.onNodeWithTag("wallpaper-video").assertExists()
            compose.runOnIdle { fullSettings.value = true }
            compose.onNodeWithTag("wallpaper-video").assertDoesNotExist()
            compose.runOnIdle { fullSettings.value = false }
            compose.onNodeWithTag("wallpaper-video").assertExists()
        } finally { clip.delete() }
    }

    @Test fun invalidVideoFallsBackToWallpaperWithoutOpaqueSurface() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invalid = File(context.cacheDir, "invalid-wallpaper.mp4").apply { writeText("not a video") }
        try {
            compose.setContent { WallpaperEngine(LauncherConfig(wallpaperPath = invalid.path, wallpaperVideo = true), false) }
            // Decoder errors and IO frame preparation complete independently; await the real fallback.
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("wallpaper-video").fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("wallpaper-still").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("wallpaper-video").assertDoesNotExist()
            compose.onNodeWithTag("wallpaper-still").assertExists()
        } finally { invalid.delete() }
    }

    @Test fun pausedHostRemovesVideoAndResumedHostRecreatesIt() {
        val clip = fixture()
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.registry.currentState = Lifecycle.State.RESUMED }
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    WallpaperEngine(LauncherConfig(wallpaperPath = clip.path, wallpaperVideo = true), false)
                }
            }
            compose.onNodeWithTag("wallpaper-video").assertExists()
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            compose.onNodeWithTag("wallpaper-video").assertDoesNotExist()
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
            compose.onNodeWithTag("wallpaper-video").assertExists()
        } finally {
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.DESTROYED }
            clip.delete()
        }
    }
}
