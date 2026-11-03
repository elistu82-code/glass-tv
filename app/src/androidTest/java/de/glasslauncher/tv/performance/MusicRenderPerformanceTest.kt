package de.glasslauncher.tv.performance

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.core.*
import de.glasslauncher.tv.media.SpotifyState
import de.glasslauncher.tv.testing.AwakeDeviceRule
import de.glasslauncher.tv.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Controlled presentation benchmark, never claimed as Spotify discovery or release performance. */
class MusicRenderPerformanceTest {
    @get:Rule(order=0) val awake=AwakeDeviceRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun sixSecondsOfPlayingProgressKeepDashboardCompositionIdle() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("renderBenchmark")=="true")
        val frames=Collections.synchronizedList(mutableListOf<LongArray>())
        val thread=HandlerThread("GlassFrameProbe").apply { start() }
        val handler=Handler(thread.looper)
        val listener=android.view.Window.OnFrameMetricsAvailableListener { _,metrics,_ ->
            frames+=longArrayOf(metrics.getMetric(FrameMetrics.TOTAL_DURATION),metrics.getMetric(FrameMetrics.DRAW_DURATION),metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
        }
        var compositions=0
        val feedback=UiSoundFeedback(SystemClock::uptimeMillis) { }
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val callbacks=LauncherCallbacks(onConfigChange={},onLaunch={},onAppInfo={},onUninstall={},onImportWallpaper={},onOpenNetwork={},onStockHome={},onExportConfig={},onImportConfig={})
        val music=SpotifyState(connected=true,isPlaying=true,title="Controlled render benchmark",artist="No Spotify discovery claim",canPrevious=true,canPlayPause=true,canNext=true,positionMs=50000,durationMs=240000,positionUpdatedAtMs=SystemClock.elapsedRealtime(),playbackSpeed=1f)
        compose.setContent {
            CompositionLocalProvider(LocalUiSounds provides feedback) {
                LauncherScreen((0..5).map { InstalledApp("bench.$it/.Main","bench.$it","App $it") },LauncherConfig(musicMode=true,uiSounds=false),callbacks,music=music) { }
                SideEffect { compositions++ }
            }
        }
        compose.waitForIdle()
        val baselineCompositions=compositions
        compose.runOnIdle { compose.activity.window.addOnFrameMetricsAvailableListener(listener,handler) }
        try {
            val started=SystemClock.elapsedRealtime()
            compose.waitUntil(10000) { SystemClock.elapsedRealtime()-started>=6000 }
        } finally {
            compose.runOnIdle { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            val drained=CountDownLatch(1);handler.post { drained.countDown() };assertTrue(drained.await(2,TimeUnit.SECONDS))
            thread.quitSafely();thread.join(2000)
        }
        val samples=synchronized(frames) { frames.toList() }
        assertTrue("The playing progress must actually produce frames",samples.size>=20)
        assertEquals("Draw-only progress must not recompose the dashboard owner",baselineCompositions,compositions)
        fun percentile(column:Int,percent:Double):Double {
            val sorted=samples.map { it[column]/1000000.0 }.sorted()
            return sorted[((sorted.size-1)*percent).toInt()]
        }
        val report=com.google.gson.JsonObject().apply {
            addProperty("controlledDebugPresentation",true);addProperty("durationMs",6000);addProperty("frames",samples.size)
            addProperty("totalP50Ms",percentile(0,.5));addProperty("totalP95Ms",percentile(0,.95))
            addProperty("drawP95Ms",percentile(1,.95));addProperty("layoutP95Ms",percentile(2,.95))
            addProperty("framesOver16_7Ms",samples.count { it[0]>16700000 });addProperty("ownerRecompositions",compositions-baselineCompositions)
        }
        val label=if(args.getString("benchmarkLabel")=="before") "before" else "after"
        File(compose.activity.cacheDir,"music-render-$label.json").writeText(report.toString())
    }
}
