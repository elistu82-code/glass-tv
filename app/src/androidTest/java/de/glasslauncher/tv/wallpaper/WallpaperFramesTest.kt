package de.glasslauncher.tv.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.core.LauncherConfig
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WallpaperFramesTest {
    @Test fun musicBlurPreservesStructureWhileSettingsRemainStronglyBlurred() {
        val original=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        for(y in 0 until 128) for(x in 0 until 128) original.setPixel(x,y,if((x/8+y/8)%2==0) Color.WHITE else Color.BLACK)
        val music=musicBlurFrame(original);val settings=blurFrame(original)
        fun variance(image:Bitmap):Double {
            val values=mutableListOf<Double>()
            for(y in 40 until image.height-40 step 10) for(x in 40 until image.width-40 step 10) values.add(Color.red(image.getPixel(x,y))/255.0)
            val mean=values.average();return values.map { (it-mean)*(it-mean) }.average()
        }
        try {
            assertTrue("Music background must retain more structure than Full Settings",variance(music)>variance(settings)*2)
            assertTrue("Music background must still soften the raw checkerboard",variance(music)<.2)
            assertFalse(original.isRecycled)
        } finally { music.recycle();settings.recycle();original.recycle() }
    }

    @Test fun exactBlurWorkingSizeKeepsOriginalReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File(context.cacheDir, "blur-regression.jpg")
        val original = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.GREEN)
        try {
            image.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            val frames = loadFrames(LauncherConfig(wallpaperPath = image.path))
            try {
                assertFalse(frames.normal.isRecycled)
                assertEquals(96, frames.normal.width)
                assertTrue(Color.green(frames.normal.getPixel(0, 0)) > 200)
                assertEquals(960, frames.blurred.width)
            } finally { frames.normal.recycle(); frames.blurred.recycle() }
        } finally { original.recycle(); image.delete() }
    }
}
