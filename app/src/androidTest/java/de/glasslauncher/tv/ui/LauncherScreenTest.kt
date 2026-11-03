package de.glasslauncher.tv.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherCallbacks
import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.media.SpotifyState
import de.glasslauncher.tv.testing.AwakeDeviceRule
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class LauncherScreenTest {
    @get:Rule(order = 0) val awake = AwakeDeviceRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestActivityAwake() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private val apps = listOf(InstalledApp("a", "example.a", "Alpha"), InstalledApp("b", "example.b", "Beta"))

    private fun show(
        installed: List<InstalledApp> = apps,
        initial: LauncherConfig = LauncherConfig(),
        music: SpotifyState = SpotifyState(),
        onPlayPause: () -> Unit = {},
        onOpenMusic: () -> Unit = {},
        owner: LifecycleOwner? = null,
        sounds: UiSoundFeedback = UiSoundFeedback(android.os.SystemClock::uptimeMillis) {},
        musicState: androidx.compose.runtime.State<SpotifyState>? = null,
        onLaunch: (InstalledApp) -> Unit = {},
    ) {
        val config = mutableStateOf(initial)
        compose.setContent {
            CompositionLocalProvider(LocalUiSounds provides sounds, LocalLifecycleOwner provides (owner ?: LocalLifecycleOwner.current)) {
            LauncherScreen(installed, config.value, LauncherCallbacks(
                onConfigChange = { config.value = it(config.value) }, onLaunch = onLaunch,
                onAppInfo = {}, onUninstall = {}, onImportWallpaper = {}, onOpenNetwork = {},
                onStockHome = {}, onExportConfig = {}, onImportConfig = {}, onMusicPlayPause = onPlayPause, onOpenMusic = onOpenMusic,
            ), music = musicState?.value ?: music) { }
            }
        }
        compose.waitForIdle()
    }

    @Test fun coverShortcutIsRemoteReachableAndRestoresFocusAfterResume() {
        val owner=object:LifecycleOwner {
            val registry=LifecycleRegistry(this)
            override val lifecycle:Lifecycle get()=registry
        }
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        val playing=SpotifyState(connected=true,isPlaying=true,title="Song",artist="Artist",canPrevious=true,canPlayPause=true,canNext=true)
        val state=mutableStateOf(playing)
        var opens=0
        show(initial=LauncherConfig(musicMode=true),musicState=state,owner=owner,onOpenMusic={opens++})
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight);pressKey(Key.DirectionUp);repeat(3) { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag("music-album-cover").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1,opens) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("music:2").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("music-album-cover").assertIsFocused()
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.CREATED;state.value=SpotifyState() }
        compose.runOnIdle { state.value=playing;owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.waitForIdle()
        compose.onNodeWithTag("music-album-cover").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("app:b").assertIsFocused()
    }

    @Test fun coverToGearKeepsHeaderFocusWhenSessionEnds() {
        val state=mutableStateOf(SpotifyState(connected=true,canPlayPause=true,canNext=true))
        show(initial=LauncherConfig(musicMode=true),musicState=state)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp);repeat(2) { pressKey(Key.DirectionRight) };pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("settings-gear").assertIsFocused()
        compose.runOnIdle { state.value=SpotifyState() }
        compose.onNodeWithTag("settings-gear").assertIsFocused()
    }

    @Test fun coverWithNoTransportActionsRemainsReachableAndSessionLossRestoresDock() {
        val state=mutableStateOf(SpotifyState(connected=true,title="Metadata only"))
        show(initial=LauncherConfig(musicMode=true),musicState=state)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("music-album-cover").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp);pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("quick-settings").assertExists()
        compose.onNodeWithTag("music-album-cover").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("app:a").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("music-album-cover").assertIsFocused()
        compose.runOnIdle { state.value=SpotifyState() }
        compose.onNodeWithTag("music-album-cover").assertDoesNotExist()
        compose.onNodeWithTag("app:a").assertIsFocused()
    }

    @Test fun spotifyDockUpVisitsControlsBeforeClockAndDownRestoresLastApp() {
        show(initial=LauncherConfig(musicMode=true),music=SpotifyState(connected=true,isPlaying=true,title="A title",artist="Artist",canPrevious=true,canPlayPause=true,canNext=true))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight);pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("music:1").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft);pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("music:0").assertIsFocused()
        compose.onRoot().performKeyInput { repeat(2) { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag("music:2").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("clock-widget").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("music:2").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("app:b").assertIsFocused()
    }

    @Test fun spotifyVisualizerAndSongInfoHaveSeparateVerticalSpace() {
        show(initial=LauncherConfig(musicMode=true,simulatedVisualizer=true),music=SpotifyState(connected=true,isPlaying=true,title="Song title",artist="Artist",canPlayPause=true))
        val visualizer=compose.onNodeWithTag("spotify-visualizer").fetchSemanticsNode().boundsInRoot
        val artist=compose.onNodeWithTag("music-artist").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Visualizer must stay beside artist without overlap",artist.right<=visualizer.left)
    }

    @Test fun spotifyLayoutIsSeparatedStableAndSessionLossReturnsToDock() {
        val state=mutableStateOf(SpotifyState(connected=true,title="Short title",artist="Artist",canPrevious=true,canPlayPause=true,canNext=true,positionMs=10000,durationMs=200000))
        show(initial=LauncherConfig(musicMode=true),musicState=state)
        fun bounds(tag:String)=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val controls=bounds("music-controls");val cover=bounds("music-album-cover");val progress=bounds("music-progress");val dock=bounds("app-row")
        org.junit.Assert.assertTrue(controls.right<cover.left)
        org.junit.Assert.assertTrue(controls.bottom<progress.top && progress.bottom<dock.top)
        org.junit.Assert.assertTrue(bounds("music-title").bottom<=bounds("music-artist").top)
        org.junit.Assert.assertTrue(bounds("music-artist").bottom<bounds("music-buttons").top)
        compose.onNodeWithTag("music:1").assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(56.dp)
        org.junit.Assert.assertTrue(bounds("music:0").right+30f<bounds("music:1").left)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight);pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("music:1").assertIsFocused()
        compose.runOnIdle { state.value=state.value.copy(title="A much longer title that wraps over two lines without moving playback buttons or cover") }
        assertEquals(controls,bounds("music-controls"));assertEquals(cover,bounds("music-album-cover"))
        compose.onNodeWithTag("music:1").assertIsFocused()
        compose.runOnIdle { state.value=SpotifyState() }
        compose.onNodeWithTag("spotify-area").assertDoesNotExist()
        compose.onNodeWithTag("app:b").assertIsFocused()
    }

    @Test fun soundsFollowRealFocusAndConfirmOnlyOnceAcrossOverlays() {
        var now=1000L
        val played=mutableListOf<UiSound>()
        show(sounds=UiSoundFeedback({now},played::add))
        compose.runOnIdle { assertEquals(emptyList<UiSound>(),played) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(emptyList<UiSound>(),played) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(listOf(UiSound.RIGHT),played); now+=200 }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(1,played.size) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertEquals(listOf(UiSound.RIGHT,UiSound.UP),played); now+=200 }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("calendar-overlay").assertExists()
        compose.runOnIdle { assertEquals(listOf(UiSound.RIGHT,UiSound.UP,UiSound.CONFIRM),played) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(UiSound.RIGHT,played.last()); assertEquals(4,played.size) }
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("clock-widget").assertIsFocused()
        compose.runOnIdle { assertEquals(4,played.size) }
    }

    @Test fun disabledUiSoundsDoNotAffectNavigationOrCalendar() {
        val played=mutableListOf<UiSound>()
        show(initial=LauncherConfig(uiSounds=false),sounds=UiSoundFeedback(android.os.SystemClock::uptimeMillis,played::add))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionUp); pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("calendar-overlay").assertExists()
        compose.runOnIdle { assertEquals(emptyList<UiSound>(),played) }
    }

    @Test fun longOkConfirmsContextOnce() {
        val played=mutableListOf<UiSound>()
        show(sounds=UiSoundFeedback(android.os.SystemClock::uptimeMillis,played::add))
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("app-context").assertExists()
        compose.runOnIdle { assertEquals(listOf(UiSound.CONFIRM),played) }
    }

    @Test fun largeDockKeepsWholeTilesVisibleWhileScrolling() {
        val many=(0..11).map { InstalledApp("example.app$it/.Main","example.app$it","App $it") }
        show(installed=many,initial=LauncherConfig(order=many.map { it.id }))
        fun checkVisibleTiles() {
            compose.waitForIdle()
            val density=compose.density.density
            val visible=many.mapNotNull { app -> compose.onAllNodesWithTag("app:${app.id}").fetchSemanticsNodes().firstOrNull() }
                // LazyRow retains unplaced semantics nodes after reverse scrolling. They are
                // not drawn; bounds from their last placement do not describe visible tiles.
                .filter { it.layoutInfo.isPlaced && it.boundsInRoot.width>1f }
            org.junit.Assert.assertTrue("At least four full tiles on the 960dp Fire TV",visible.size>=4)
            visible.forEach { node -> org.junit.Assert.assertEquals("Partially clipped tile: ${visible.map { "${it.id}: ${it.boundsInRoot}, placed=${it.layoutInfo.isPlaced}" }} viewport=${compose.onNodeWithTag("dock-viewport").fetchSemanticsNode().boundsInRoot}",182f*density,node.boundsInRoot.width,2f) }
        }
        checkVisibleTiles()
        repeat(6) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; checkVisibleTiles() }
        repeat(6) { compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }; checkVisibleTiles() }
    }

    @Test fun remoteNavigatesToQuickSettingsAndBackRestoresApp() {
        show()
        compose.onNodeWithTag("app:a").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("quick-settings").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("app:a").assertIsFocused()
    }

    @Test fun heldOkOpensContextWithoutLaunching() {
        var launches = 0
        show { launches++ }
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("app-context").assertExists()
        assertEquals(0, launches)
    }

    @Test fun regularOkLaunchesFocusedApp() {
        var launched = ""
        show { launched = it.id }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("b", launched) }
    }

    @Test fun menuSearchFiltersVisibleAppsAndBackRestoresFocus() {
        show(initial = LauncherConfig(hidden = setOf("a")))
        compose.onNodeWithTag("app:b").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("app-search").assertExists()
        compose.onNodeWithTag("search-query").performTextInput("Beta")
        compose.onNodeWithTag("search-result:b").assertExists()
        compose.onNodeWithTag("search-result:a").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("app:b").assertIsFocused()
    }

    @Test fun searchResultsLaunchUsingRemote() {
        var launched = ""
        show { launched = it.id }
        compose.onRoot().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("search-query").performTextInput("Beta")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("b", launched) }
    }

    @Test fun fullSettingsHidesAppsAndBackReversesNavigation() {
        show()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        // RemoteButton is driven by D-pad (and exposes accessibility actions), not touch.
        compose.onRoot().performKeyInput {
            repeat(3) { pressKey(Key.DirectionDown) }
            pressKey(Key.DirectionCenter)
        }
        compose.onNodeWithTag("full-settings").assertExists()
        compose.onNodeWithTag("app-row").assertDoesNotExist()
        compose.onRoot().performKeyInput {
            repeat(2) { pressKey(Key.DirectionDown) }
            pressKey(Key.DirectionCenter)
        }
        compose.onNodeWithText("Apps suchen").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("Einstellungen").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("quick-settings").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("app:a").assertIsFocused()
    }

    @Test fun hiddenOrEmptyAppListLeavesSettingsReachable() {
        show(initial = LauncherConfig(hidden = setOf("a", "b")))
        compose.onNodeWithTag("settings-gear").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("quick-settings").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("settings-gear").assertIsFocused()
    }

    @Test fun emptyDiscoveryLeavesSettingsReachable() {
        show(installed = emptyList())
        compose.onNodeWithTag("settings-gear").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("quick-settings").assertExists()
    }

    @Test fun reorderBackCancelsWithoutChangingAppOrder() {
        show()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter); pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Back) }
        compose.onNodeWithTag("app:a").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("app:b").assertIsFocused()
    }

    @Test fun dockUpVisitsClockAndRightGearDownRestoresLastApp() {
        show()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("clock-widget").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("settings-gear").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("app:b").assertIsFocused()
        compose.onNodeWithTag("app:b").assertWidthIsEqualTo(182.dp).assertHeightIsEqualTo(94.dp)
    }

    @Test fun clockOkOpensCalendarAndBackRestoresClock() {
        show()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("clock-widget").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("calendar-overlay").assertIsDisplayed()
        compose.onNodeWithTag("app-row").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("clock-widget").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("app:b").assertIsFocused()
    }

    @Test fun dashboardContainsNoTutorialFooter() {
        show()
        compose.onNodeWithText("OK öffnen", substring = true).assertDoesNotExist()
    }

    @Test fun shortDockCentersTilesWithinGlassContainer() {
        show()
        val row = compose.onNodeWithTag("app-row").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("app:b").fetchSemanticsNode().boundsInRoot
        assertEquals(row.center.x, (first.left + last.right) / 2, 1f)
    }

    @Test fun repeatedHorizontalInputNeverLeavesDockEdges() {
        show()
        compose.onRoot().performKeyInput { repeat(3) { pressKey(Key.DirectionLeft) } }
        compose.onNodeWithTag("app:a").assertIsFocused()
        compose.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag("app:b").assertIsFocused()
    }
    @Test fun launchTransitionIsCancellableBeforeExternalIntent() {
        var launches = 0
        show { launches++ }
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("launch-transition").assertExists()
            compose.runOnIdle { assertEquals(0, launches) }
            compose.onRoot().performKeyInput { pressKey(Key.Back) }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("launch-transition").assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, launches) }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun completedLaunchDispatchesExactlyOnce() {
        var launches = 0
        show { launches++ }
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle { assertEquals(0, launches) }
            compose.mainClock.advanceTimeBy(600)
            compose.runOnIdle { assertEquals(1, launches) }
            compose.mainClock.advanceTimeBy(600)
            compose.runOnIdle { assertEquals(1, launches) }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun musicControlsRequireSessionAndAreRemoteReachable() {
        var toggles = 0
        show(initial = LauncherConfig(musicMode = true), music = SpotifyState(connected = true,
            isPlaying = true, title = "Fixture", canPlayPause = true), onPlayPause = { toggles++ })
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("music:1").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("app:a").assertIsFocused()
    }

    @Test fun musicModeWithoutSessionNeverShowsActiveControls() {
        show(initial = LauncherConfig(musicMode = true))
        compose.onNodeWithTag("music-controls").assertDoesNotExist()
    }

    @Test fun pausingDuringLaunchCancelsUndispatchedIntent() {
        class Owner : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val owner = Owner()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.registry.currentState = Lifecycle.State.RESUMED }
        var launches = 0
        show(owner = owner) { launches++ }
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            compose.mainClock.advanceTimeBy(650)
            compose.runOnIdle { assertEquals(0, launches) }
            compose.onNodeWithTag("launch-transition").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun musicControlsCannotStealFocusBehindQuickSettings() {
        show(initial = LauncherConfig(musicMode = true), music = SpotifyState(connected = true, canPlayPause = true))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("quick-settings").assertExists()
        compose.onNodeWithTag("music:1").assertIsNotEnabled()
    }

    @Test fun longDockScrollsAndRestoresFocusWithoutLeavingRow() {
        val many = (0..11).map { InstalledApp("example.app$it/.Main", "example.app$it", "App $it") }
        show(installed = many, initial = LauncherConfig(order = many.map { it.id }))
        compose.onRoot().performKeyInput { repeat(15) { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag("app:${many.last().id}").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("clock-widget").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("app:${many.last().id}").assertIsFocused()
    }

}
