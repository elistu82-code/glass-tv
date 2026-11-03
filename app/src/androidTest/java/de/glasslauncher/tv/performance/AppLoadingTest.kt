package de.glasslauncher.tv.performance

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppLoadingTest {
    @Test fun iconRasterizationRunsOffMainCachesAndPreservesAspect() = runBlocking {
        var draws=0
        val drawable=object:Drawable() {
            override fun getIntrinsicWidth()=128
            override fun getIntrinsicHeight()=64
            override fun draw(canvas:Canvas) { check(Looper.myLooper()!=Looper.getMainLooper()); draws++; canvas.drawColor(android.graphics.Color.BLUE) }
            override fun setAlpha(alpha:Int) = Unit
            override fun setColorFilter(filter:ColorFilter?) = Unit
            @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.OPAQUE
        }
        val app=InstalledApp("test.icon/.Main","test.icon","Fixture",icon=drawable)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val first=requireNotNull(AppIcons.load(context,app)); val second=AppIcons.load(context,app)
        assertSame(first,second); assertEquals(1,draws); assertEquals(first.height*2,first.width)
        assertEquals(android.graphics.Color.BLUE,first.getPixel(first.width/2,first.height/2))
        AppIcons.invalidatePackage(app.packageName)
        assertNotSame(first,AppIcons.load(context,app)); assertEquals(2,draws)
    }
    @Test fun largerWideLogosKeepAspectAndCacheEvictsOldEntries() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val logo=object:Drawable() {
            override fun getIntrinsicWidth()=384
            override fun getIntrinsicHeight()=96
            override fun draw(canvas:Canvas) { check(Looper.myLooper()!=Looper.getMainLooper()); canvas.drawColor(android.graphics.Color.BLUE) }
            override fun setAlpha(alpha:Int) = Unit
            override fun setColorFilter(filter:ColorFilter?) = Unit
            @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.OPAQUE
        }
        val first=InstalledApp("test.wide0/.Main","test.wide0","Wide",icon=logo)
        val image=requireNotNull(AppIcons.load(context,first))
        assertEquals(image.height*4,image.width)
        assertTrue(image.width>=424) // A 232dp tile on the actual 320dpi TV, with inner logo padding.
        assertEquals(android.graphics.Color.BLUE,image.getPixel(image.width-1,image.height-1))
        var last=first
        repeat(48) { index ->
            last=InstalledApp("test.wide${index+1}/.Main","test.wide${index+1}","Wide",icon=logo)
            AppIcons.load(context,last)
        }
        assertNull("Old bitmaps must be evicted instead of growing with the app count",AppIcons.cached(first))
        assertNotNull(AppIcons.cached(last))
    }

    @Test fun wallpaperPublishesNormalImageBeforeAnyBlurAndReusesBoth() = runBlocking {
        val key="fixture-${System.nanoTime()}"
        var decodes=0
        val memory=de.glasslauncher.tv.wallpaper.WallpaperMemory
        val image=memory.image(key) { check(Looper.myLooper()!=Looper.getMainLooper()); decodes++; android.graphics.Bitmap.createBitmap(96,54,android.graphics.Bitmap.Config.ARGB_8888) }
        assertSame(image,memory.image(key) { error("Unexpected decode") }); assertEquals(1,decodes)
        assertNull(memory.cached(key,true))
        val blur=memory.blurred(key,image)
        assertSame(blur,memory.blurred(key,image)); assertNotSame(image,blur)
    }
    @Test fun mediaExchangeSupportsValidatedPrivateWallpaperCopiesAndHostBackups() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(requireNotNull(context.externalMediaDirs.firstOrNull()),"exchange")
        val exchange=de.glasslauncher.tv.system.LocalFileExchange(root)
        val fixture=File(exchange.imports,"glass-050-probe.png")
        val bitmap=android.graphics.Bitmap.createBitmap(160,90,android.graphics.Bitmap.Config.ARGB_8888)
        fixture.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",fixture)
        val stored=de.glasslauncher.tv.wallpaper.WallpaperStore(context).import(uri)
        assertTrue(File(stored.path).isFile)
        assertTrue(File(stored.path).canonicalPath.startsWith(context.filesDir.canonicalPath+"/"))
        File(stored.path).delete(); fixture.delete(); bitmap.recycle()
        val backup=exchange.exportConfig(de.glasslauncher.tv.data.ConfigCodec.encode(LauncherConfig()))
        val backupUri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",backup)
        val json=context.contentResolver.openInputStream(backupUri)!!.bufferedReader().use { it.readText() }
        assertEquals(LauncherConfig(),de.glasslauncher.tv.data.ConfigCodec.decode(json))
    }
    @Test fun actualAppsSnapshotRoundTripsAndDefaultToolsStayFiltered() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=AppRepository(context)
        val apps=repo.discover()
        assertEquals(apps,repo.cachedDiscovery())
        assertTrue(apps.all { it.icon==null })
        val visible=AppOrdering.visibleApps(apps,LauncherConfig())
        assertFalse(visible.any { it.packageName=="com.amazon.firetv.troubleshooting" })
        for(pkg in listOf("com.amazon.firebat","com.amazon.avod.thirdpartyclient","com.netflix.ninja","com.amazon.firetv.youtube","com.spotify.tv.android","org.jellyfin.androidtv")) {
            if(apps.any { it.packageName==pkg }) assertTrue("$pkg missing",visible.any { it.packageName==pkg })
        }
        File(context.cacheDir,"app-audit.tsv").writeText(apps.joinToString("\n") {
            "${it.label}\t${it.packageName}\t${it.id}\tsystem=${it.isSystem}\tdefaultHidden=${SystemAppPolicy.hiddenByDefault(it)}"
        })
    }
}
