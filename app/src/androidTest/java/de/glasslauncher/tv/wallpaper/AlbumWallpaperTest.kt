package de.glasslauncher.tv.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.testing.AwakeDeviceRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AlbumWallpaperTest {
    @get:Rule(order = 0) val awake = AwakeDeviceRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun screenOn() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }

    @Test fun musicModeUsesBlurredStaticWallpaperAndExitRestoresOriginal() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val image=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        for(y in 0 until 128) for(x in 0 until 128) image.setPixel(x,y,if((x/8+y/8)%2==0) Color.WHITE else Color.BLACK)
        val file=java.io.File(context.cacheDir,"music-blur-fixture.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        val active=mutableStateOf(true)
        compose.setContent { WallpaperEngine(LauncherConfig(wallpaperPath=file.path),false,musicActive=active.value) }
        try {
            compose.onNodeWithTag("music-blurred-background").assertExists()
            compose.waitUntil(10000) {
                val nodes=compose.onAllNodesWithTag("wallpaper-still").fetchSemanticsNodes()
                if(nodes.size!=1) false else {
                    val pixels=compose.onNodeWithTag("wallpaper-still").captureToImage().toPixelMap()
                    val a=pixels[pixels.width*7/32,pixels.height*3/5];val b=pixels[pixels.width*9/32,pixels.height*3/5]
                    kotlin.math.abs(a.red-b.red) in .03f.. .5f && (a.red+b.red)/2 in .15f.. .45f && kotlin.math.abs(a.red-a.green)<.05f
                }
            }
            compose.onNodeWithTag("wallpaper-video").assertDoesNotExist()
            compose.runOnIdle { active.value=false }
            compose.onNodeWithTag("music-blurred-background").assertDoesNotExist()
            compose.waitUntil(10000) {
                if(compose.onAllNodesWithTag("wallpaper-still").fetchSemanticsNodes().size!=1) false else {
                    val pixels=compose.onNodeWithTag("wallpaper-still").captureToImage().toPixelMap()
                    kotlin.math.abs(pixels[pixels.width*7/32,pixels.height*3/5].red-pixels[pixels.width*9/32,pixels.height*3/5].red)>.5f
                }
            }
        } finally { file.delete() }
    }

    @Test fun trackChangesKeepTheSelectedWallpaperAndCachedMusicBlur() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        fun solid(color:Int)=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file=java.io.File(context.cacheDir,"stable-music-wallpaper.png")
        solid(Color.RED).let { image -> file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle() }
        val art=mutableStateOf<Bitmap?>(solid(Color.GREEN))
        val config=LauncherConfig(wallpaperPath=file.path)
        compose.setContent {
            androidx.compose.foundation.layout.Box {
                WallpaperEngine(config,false,musicActive=true)
                de.glasslauncher.tv.ui.MusicAlbumCover(de.glasslauncher.tv.media.SpotifyState(artwork=art.value),
                    androidx.compose.ui.Modifier.size(80.dp))
            }
        }
        fun redBackground()=compose.onNodeWithTag("wallpaper-still").captureToImage().toPixelMap().let { it[it.width/2,it.height/2].let { c -> c.red>.2f && c.green<.05f && c.blue<.05f } }
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithTag("wallpaper-still").fetchSemanticsNodes().size==1 }
            org.junit.Assert.assertTrue("Music background must use selected wallpaper, never green album art",redBackground())
            val key="${config.theme}|${config.wallpaperPath}|${config.wallpaperVideo}"
            val cached=WallpaperMemory.cachedMusic(key)
            org.junit.Assert.assertNotNull(cached)
            compose.runOnIdle { art.value=solid(Color.BLUE) }
            compose.waitForIdle()
            org.junit.Assert.assertTrue("Track changes must not recolor the wallpaper",redBackground())
            org.junit.Assert.assertSame("No wallpaper decode/blur on a track change",cached,WallpaperMemory.cachedMusic(key))
        } finally { file.delete() }
    }
}
