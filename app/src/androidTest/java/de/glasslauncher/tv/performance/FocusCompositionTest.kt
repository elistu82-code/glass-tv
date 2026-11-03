package de.glasslauncher.tv.performance

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.tooling.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.core.*
import de.glasslauncher.tv.testing.AwakeDeviceRule
import de.glasslauncher.tv.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalComposeRuntimeApi::class,InternalComposeApi::class,ComposeToolingApi::class,ExperimentalTestApi::class)
class FocusCompositionTest {
    @get:Rule(order=0) val awake=AwakeDeviceRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun dockNavigationMeasuresActualRecomposeScopes() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("renderBenchmark")=="true")
        var scopes=0;var passes=0
        val observer=object:CompositionObserver {
            override fun onBeginComposition(composition:ObservableComposition) { passes++ }
            override fun onEndComposition(composition:ObservableComposition) { }
            override fun onScopeEnter(scope:RecomposeScope) { scopes++ }
            override fun onScopeExit(scope:RecomposeScope) { }
            override fun onReadInScope(scope:RecomposeScope,value:Any) { }
            override fun onScopeInvalidated(scope:RecomposeScope,value:Any?) { }
            override fun onScopeDisposed(scope:RecomposeScope) { }
        }
        val apps=(0..15).map { InstalledApp("scope.$it/.Main","scope.$it","App $it") }
        val config=LauncherConfig(order=apps.map { it.id })
        val callbacks=LauncherCallbacks(onConfigChange={},onLaunch={},onAppInfo={},onUninstall={},onImportWallpaper={},onOpenNetwork={},onStockHome={},onExportConfig={},onImportConfig={})
        val sounds=UiSoundFeedback(android.os.SystemClock::uptimeMillis) { }
        compose.setContent {
            val composition=currentComposer.composition
            DisposableEffect(composition) {
                val handle=composition.setObserver(observer)
                onDispose { handle?.dispose() }
            }
            CompositionLocalProvider(LocalUiSounds provides sounds) { LauncherScreen(apps,config,callbacks) { } }
        }
        compose.waitForIdle()
        val counts=mutableListOf<Int>();val compositions=mutableListOf<Int>()
        repeat(24) { index ->
            compose.runOnIdle { scopes=0;passes=0 }
            compose.onRoot().performKeyInput { pressKey(if(index<12) Key.DirectionRight else Key.DirectionLeft) }
            compose.waitForIdle()
            counts+=scopes;compositions+=passes
        }
        compose.onNodeWithTag("app:scope.0/.Main").assertIsFocused()
        assertTrue("Probe must observe real composition work",counts.sum()>0)
        val label=if(args.getString("benchmarkLabel")=="before") "before" else "after"
        File(compose.activity.cacheDir,"focus-scopes-$label.json").writeText(com.google.gson.JsonObject().apply {
            add("scopeEntriesPerMove",com.google.gson.Gson().toJsonTree(counts))
            add("compositionPassesPerMove",com.google.gson.Gson().toJsonTree(compositions))
            addProperty("scopeEntriesTotal",counts.sum());addProperty("compositionPassesTotal",compositions.sum())
            addProperty("controlledDebugOnly",true);addProperty("nativeAudioInjected",false)
        }.toString())
    }
}
