package de.glasslauncher.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import de.glasslauncher.tv.media.SpotifyState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherCallbacks
import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.data.AppOrdering
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Panel { HOME, QUICK, FULL, CONTEXT, CALENDAR, SEARCH, WEATHER }
internal enum class SettingsPage { MAIN, WALLPAPER, ANIMATIONS, APPS, MUSIC, SYSTEM }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    apps: List<InstalledApp>,
    config: LauncherConfig,
    callbacks: LauncherCallbacks,
    music: SpotifyState = SpotifyState(),
    homeRequest: Int = 0,
    appsLoading: Boolean = false,
    onDockReady: () -> Unit = {},
    wallpaper: @Composable (fullSettings: Boolean) -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE2EDE7), onBackground = Color.White)) {
        val sounds=rememberUiSounds(config)
        CompositionLocalProvider(LocalUiSounds provides sounds, LocalGlassConfig provides config, LocalContentColor provides Color.White) {
            var panel by remember { mutableStateOf(Panel.HOME) }
            var returnToClock by remember { mutableStateOf(false) }
            var searchOrigin by remember { mutableStateOf(Panel.HOME) }
            var page by remember { mutableStateOf(SettingsPage.MAIN) }
            var selectedId by remember { mutableStateOf<String?>(null) }
            var contextId by remember { mutableStateOf<String?>(null) }
            var movingId by remember { mutableStateOf<String?>(null) }
            var movingOrder by remember { mutableStateOf<List<String>?>(null) }
            var selectedX by remember { mutableFloatStateOf(28f) }
            val appPositions = remember { mutableMapOf<String, Float>() }
            val focusers = remember { mutableMapOf<String, FocusRequester>() }
            val weatherFocus = remember { FocusRequester() }
            val previousFocus = remember { FocusRequester() }
            val playFocus = remember { FocusRequester() }
            val nextFocus = remember { FocusRequester() }
            val gearFocus = remember { FocusRequester() }
            val coverFocus = remember { FocusRequester() }
            var coverOwnsFocus by remember { mutableStateOf(false) }
            var returnToCover by remember { mutableStateOf(false) }
            var coverReturnApp by remember { mutableStateOf<String?>(null) }
            var musicOwnsFocus by remember { mutableStateOf(false) }
            val clockFocus = remember { FocusRequester() }
            val overlayFocus = remember { FocusRequester() }
            val lazyState = rememberLazyListState()
            val density = LocalDensity.current
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
            var launching by remember { mutableStateOf<InstalledApp?>(null) }
            var enteredApp by remember { mutableStateOf(false) }
            var launchDispatched by remember { mutableStateOf(false) }
            val launchProgress = remember { Animatable(0f) }
            val bounds = remember { mutableMapOf<String, Rect>() }
            val homeAlpha by animateFloatAsState(if (foreground) 1f else .94f,
                tween(AnimationTokens.duration(AnimationTokens.homeReturn, config), easing = AnimationTokens.easing(config)), label = "dashboardReturn")
            DisposableEffect(lifecycle) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        if(coverOwnsFocus) { returnToCover=true;coverReturnApp=selectedId }
                        foreground = false
                        if (launching != null) { if (launchDispatched) enteredApp = true else launching = null }
                    }
                    if (event == Lifecycle.Event.ON_RESUME) { foreground = true; if (enteredApp) { launching = null; enteredApp = false } }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(launching) {
                val app = launching ?: return@LaunchedEffect
                enteredApp = false
                launchDispatched = false
                launchProgress.snapTo(0f)
                launchProgress.animateTo(1f, tween(AnimationTokens.duration(AnimationTokens.appLaunch, config), easing = AnimationTokens.easing(config)))
                if (!foreground) { launching = null; return@LaunchedEffect }
                launchDispatched = true
                callbacks.onLaunch(app)
                // A failed or unsupported external intent must not leave an opaque cover stuck.
                delay(2_000)
                if (!enteredApp) launching = null
            }
            val musicActive = config.musicMode && music.connected
            var lastMusicIndex by remember { mutableIntStateOf(1) }
            val musicFocusers=listOf(previousFocus,playFocus,nextFocus)
            val musicEnabled=listOf(music.canPrevious,music.canPlayPause,music.canNext)
            val entryIndex=lastMusicIndex.takeIf { musicEnabled[it] } ?: 1.takeIf { music.canPlayPause } ?: musicEnabled.indexOfFirst { it }.takeIf { it>=0 }
            val musicNavigable=musicActive
            val firstMusicFocus=entryIndex?.let { musicFocusers[it] } ?: if(musicActive) coverFocus else clockFocus
            val dockUpFocus=if(musicNavigable) firstMusicFocus else clockFocus
            fun dockFocus(): FocusRequester = selectedId?.let { focusers[it] } ?: weatherFocus
            val baseApps = remember(apps, config.order, config.hidden, config.shownSystemApps) {
                AppOrdering.visibleApps(apps, config)
            }
            val visibleApps = remember(baseApps, movingOrder) {
                val order = movingOrder?.withIndex()?.associate { it.value to it.index }
                if (order == null) baseApps else baseApps.sortedBy { order[it.id] ?: Int.MAX_VALUE }
            }
            val appIds = visibleApps.map { it.id }
            // Capture before disappearing controls trigger Compose's automatic focus recovery.
            val restoreMusicFocus=!musicActive && foreground && (musicOwnsFocus || coverOwnsFocus) && panel==Panel.HOME && movingId==null
            // Do not subscribe the whole dashboard to every dock focus change.
            val musicReturnApp=if(restoreMusicFocus) selectedId?.takeIf { it in appIds } ?: appIds.firstOrNull() else null
            LaunchedEffect(musicActive) {
                if(restoreMusicFocus) {
                    musicOwnsFocus=false;coverOwnsFocus=false;withFrameNanos { }
                    selectedId=musicReturnApp
                    (musicReturnApp?.let { focusers[it] } ?: weatherFocus).requestFocus()
                }
            }
            LaunchedEffect(foreground,musicActive,returnToCover) {
                if(foreground && returnToCover && panel==Panel.HOME) {
                    val id=coverReturnApp?.takeIf { it in appIds } ?: appIds.firstOrNull()
                    selectedId=id
                    if(id!=null && lazyState.layoutInfo.visibleItemsInfo.none { it.key==id }) lazyState.scrollToItem(appIds.indexOf(id))
                    withFrameNanos { }
                    if(musicActive) coverFocus.requestFocus()
                    else (id?.let { focusers[it] } ?: clockFocus).requestFocus()
                    returnToCover=false
                }
            }
            fun closeCalendar() { returnToClock = true; panel = Panel.HOME }
            fun restoreHome() { panel = Panel.HOME }
            fun openSearch() { searchOrigin = panel; panel = Panel.SEARCH }
            fun back() {
                when {
                    launching != null -> launching = null
                    movingId != null -> { movingId = null; movingOrder = null }
                    panel == Panel.CALENDAR -> closeCalendar()
                    panel == Panel.SEARCH -> panel = searchOrigin
                    panel == Panel.FULL && page != SettingsPage.MAIN -> page = SettingsPage.MAIN
                    panel == Panel.FULL -> panel = Panel.QUICK
                    panel != Panel.HOME -> restoreHome()
                    else -> callbacks.onStockHome()
                }
            }
            LaunchedEffect(homeRequest) {
                if (homeRequest > 0) { launching = null; movingId = null; movingOrder = null; returnToClock = false; panel = Panel.HOME }
            }
            BackHandler { back() }
            LaunchedEffect(panel, page, appIds, movingId, appIds.isEmpty() && !appsLoading) {
                withFrameNanos { }
                if (panel == Panel.HOME) {
                    val id = selectedId?.takeIf { it in appIds } ?: appIds.firstOrNull()
                    selectedId = id
                    if (returnToClock) { returnToClock = false; clockFocus.requestFocus() }
                    else if (id == null) { gearFocus.requestFocus(); if (!appsLoading) onDockReady() }
                    else {
                        lazyState.scrollToItem(appIds.indexOf(id))
                        withFrameNanos { }
                        focusers[id]?.requestFocus()
                        onDockReady()
                    }
                } else overlayFocus.requestFocus()
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().background(Color(0xFF142C29)).onPreviewKeyEvent { sounds.key(it); false }.onPreviewKeyEvent { event ->
                    if (event.key == Key.Back) {
                        if (event.type == KeyEventType.KeyUp) back()
                        true
                    } else if (launching != null) {
                        true
                    } else if (panel == Panel.HOME && movingId == null && event.key == Key.Menu) {
                        if (event.type == KeyEventType.KeyUp) { sounds.confirm(); openSearch() }
                        true
                    } else if (movingId != null) {
                        if (event.type == KeyEventType.KeyDown) {
                            val delta = when (event.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                            if (delta != 0) {
                                movingOrder = AppOrdering.move(movingOrder.orEmpty(), movingId!!, delta)
                            }
                        } else if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            sounds.confirm()
                            val order = movingOrder.orEmpty()
                            callbacks.onConfigChange { it.copy(order = order + it.order.filterNot { id -> id in order }) }
                            movingId = null; movingOrder = null
                        }
                        true
                    } else false
                },
            ) {
                val viewportWidth = maxWidth
                val viewportHeight = maxHeight
                Box(Modifier.fillMaxSize().behindLaunch(launching != null, launching?.let { bounds[it.id] }) { launchProgress.value }) {
                val hidesDashboard = panel == Panel.FULL || panel == Panel.SEARCH || panel == Panel.CALENDAR
                wallpaper(hidesDashboard)
                if (!hidesDashboard) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }) {
                    Row(Modifier.fillMaxWidth().padding(GlassTokens.screenInset), horizontalArrangement = Arrangement.SpaceBetween) {
                        RemoteButton("Wetter öffnen", Modifier.focusRequester(weatherFocus).testTag("weather-widget")
                            .focusProperties { canFocus = panel == Panel.HOME; left = gearFocus; right = if (musicNavigable) firstMusicFocus else clockFocus; down = if(musicNavigable) firstMusicFocus else dockFocus(); up = weatherFocus },
                            onFocused={musicOwnsFocus=false;coverOwnsFocus=false}, onClick = { panel = Panel.WEATHER }) {
                            Column {
                            Text("WETTER", fontSize = 10.sp, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = .55f))
                            Text("—  Offline", fontSize = 16.sp, color = Color.White.copy(alpha = .8f))
                            }
                        }
                        RemoteButton("Schnelleinstellungen", Modifier.focusRequester(gearFocus)
                            .focusProperties { canFocus = panel == Panel.HOME; left = clockFocus; right = weatherFocus; down = if(musicNavigable) firstMusicFocus else dockFocus(); up = gearFocus }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    if(musicNavigable) firstMusicFocus.requestFocus() else selectedId?.let { focusers[it]?.requestFocus() }; true
                                } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                    clockFocus.requestFocus(); true
                                } else false
                            }.testTag("settings-gear"), onFocused={musicOwnsFocus=false;coverOwnsFocus=false}, onClick = { panel = Panel.QUICK }) {
                            Text("⚙", fontSize = 24.sp)
                        }
                    }
                    var now by remember { mutableStateOf(LocalDateTime.now()) }
                    LaunchedEffect(lifecycle) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            while (true) {
                                now = LocalDateTime.now()
                                delay((60L - now.second) * 1_000)
                            }
                        }
                    }
                    RemoteButton("Kalender öffnen", Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
                        .focusRequester(clockFocus).testTag("clock-widget").focusProperties { canFocus = panel == Panel.HOME; left = weatherFocus; right = gearFocus; down = if(musicNavigable) firstMusicFocus else dockFocus(); up = clockFocus }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                if(musicNavigable) firstMusicFocus.requestFocus() else selectedId?.let { focusers[it]?.requestFocus() }; true
                            } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                gearFocus.requestFocus(); true
                            } else false
                        }, onFocused={musicOwnsFocus=false;coverOwnsFocus=false}, onClick = { panel = Panel.CALENDAR }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 23.sp, fontWeight = FontWeight.Light)
                            Text(now.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", Locale.GERMAN)), fontSize = 11.sp, color = Color.White.copy(alpha = .72f))
                        }
                    }
                    var lastMusic by remember { mutableStateOf(music) }
                    if(musicActive) SideEffect { lastMusic=music }
                    AnimatedVisibility(musicActive,
                        enter=fadeIn(tween(AnimationTokens.duration(AnimationTokens.music,config))),
                        exit=fadeOut(tween(AnimationTokens.duration(AnimationTokens.music,config))),
                        modifier=Modifier.fillMaxSize().testTag("spotify-area")) {
                        CompositionLocalProvider(LocalRemoteInputEnabled provides (musicActive && panel==Panel.HOME && movingId==null)) {
                            val displayed=if(musicActive) music else lastMusic
                            val inset=viewportWidth*(1f-GlassTokens.dockWidthFraction)/2
                            Box(Modifier.fillMaxSize()) {
                                MusicControls(displayed.copy(isPlaying=musicActive && displayed.isPlaying),config.showMusicInfo,callbacks,
                                    previousFocus,playFocus,nextFocus,weatherFocus,clockFocus,{dockFocus()},
                                    Modifier.align(Alignment.BottomStart).padding(start=(inset-MusicLayout.controlsLeftShift).coerceAtLeast(GlassTokens.screenInset),bottom=GlassTokens.aboveDock+MusicLayout.controlsLift).width(viewportWidth*MusicLayout.infoWidthFraction),
                                    onFocused={lastMusicIndex=it;musicOwnsFocus=true;coverOwnsFocus=false},cover=coverFocus)
                                AnimatedVisibility(panel==Panel.HOME,
                                    enter=fadeIn(tween(AnimationTokens.duration(AnimationTokens.quickSettings,config))),
                                    exit=fadeOut(tween(AnimationTokens.duration(AnimationTokens.quickSettings,config))),
                                    modifier=Modifier.align(Alignment.TopEnd).padding(top=MusicLayout.coverTop,end=inset)) {
                                    RemoteButton("Spotify öffnen",Modifier.size(MusicLayout.coverSize).testTag("music-album-cover")
                                        .focusRequester(coverFocus).focusProperties {
                                            canFocus=musicActive && foreground && panel==Panel.HOME && movingId==null
                                            left=entryIndex?.let { musicFocusers[it] } ?: clockFocus
                                            right=coverFocus;up=gearFocus;down=dockFocus()
                                        },enabled=musicActive,contentPadding=PaddingValues(0.dp),
                                        onFocused={coverOwnsFocus=true;musicOwnsFocus=false},onClick=callbacks.onOpenMusic,
                                        shape=RoundedCornerShape(22.dp)) { MusicAlbumCover(displayed,Modifier.fillMaxSize()) }
                                }
                                MusicProgress(displayed.copy(isPlaying=musicActive && displayed.isPlaying),
                                    Modifier.align(Alignment.BottomCenter).padding(bottom=GlassTokens.aboveDock+MusicLayout.progressLift).fillMaxWidth(GlassTokens.dockWidthFraction))
                            }
                        }
                    }
                    if (movingId != null) Text("Verschieben: ◀ ▶  ·  OK speichern  ·  Zurück abbrechen",
                        Modifier.align(Alignment.BottomCenter).padding(bottom = GlassTokens.aboveDock), fontSize = 13.sp)
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(GlassTokens.dockWidthFraction)
                        .padding(bottom = GlassTokens.rowBottom).glassSurface(config).testTag("app-row")) {
                        if (visibleApps.isEmpty()) {
                            Text(if (appsLoading) "Apps werden geladen …" else "Keine sichtbaren Apps · Über ⚙ ausgeblendete Apps wieder einblenden",
                                Modifier.padding(24.dp), fontSize = 15.sp)
                        } else {
                        val viewport=GlassTokens.dockViewportWidth(viewportWidth*GlassTokens.dockWidthFraction)
                        val inset=with(androidx.compose.ui.platform.LocalDensity.current) { GlassTokens.dockHorizontalPadding.toPx() }
                        val bring=remember(inset) { object:BringIntoViewSpec {
                            override fun calculateScrollDistance(offset: Float,size: Float,containerSize: Float): Float = when {
                                offset<inset -> offset-inset
                                offset+size>containerSize-inset -> offset+size-(containerSize-inset)
                                else -> 0f
                            }
                        } }
                        CompositionLocalProvider(LocalBringIntoViewSpec provides bring) {
                        LazyRow(modifier = Modifier.align(Alignment.Center).width(viewport).testTag("dock-viewport"), state = lazyState, contentPadding = PaddingValues(horizontal = GlassTokens.dockHorizontalPadding, vertical = GlassTokens.dockVerticalPadding), horizontalArrangement = Arrangement.spacedBy(GlassTokens.appSpacing, Alignment.CenterHorizontally)) {
                            items(visibleApps, key = { it.id }) { app ->
                                val requester = focusers.getOrPut(app.id) { FocusRequester() }
                                RemoteButton(app.label, Modifier.width(GlassTokens.appWidth).height(GlassTokens.appHeight)
                                    .background(GlassTokens.appTileBackground, RoundedCornerShape(16.dp))
                                    .focusRequester(requester).focusProperties { canFocus = panel == Panel.HOME; up = dockUpFocus; left = if (app.id == appIds.firstOrNull()) requester else FocusRequester.Default; right = if (app.id == appIds.lastOrNull()) requester else FocusRequester.Default }
                                    .onGloballyPositioned { appPositions[app.id] = with(density) { it.positionInRoot().x.toDp().value }; bounds[app.id] = it.boundsInRoot() }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) { dockUpFocus.requestFocus(); true } else false
                                    }.testTag("app:${app.id}"),
                                    contentPadding = PaddingValues(GlassTokens.appTilePadding),
                                    onFocused = { selectedId = app.id; musicOwnsFocus=false;coverOwnsFocus=false },
                                    onLongClick = {
                                        selectedX = appPositions[app.id] ?: 28f
                                        contextId = app.id; panel = Panel.CONTEXT
                                    },
                                    onClick = { launching = app }) {
                                    AppTileContent(app, app.id in config.pinned)
                                }
                            }
                        }
                        }
                        }
                    }

                }
                }
                val settingsVisible = panel == Panel.QUICK || panel == Panel.FULL
                AnimatedVisibility(
                    visible = settingsVisible,
                    enter = fadeIn(tween(AnimationTokens.duration(AnimationTokens.quickSettings, config))),
                    exit = fadeOut(tween(AnimationTokens.duration(AnimationTokens.quickSettings, config))),
                    modifier = Modifier.align(Alignment.TopEnd).padding(GlassTokens.screenInset),
                ) {
                    var lastExpanded by remember { mutableStateOf(false) }
                    if (settingsVisible) SideEffect { lastExpanded = panel == Panel.FULL }
                    val expanded = if (settingsVisible) panel == Panel.FULL else lastExpanded
                    val duration = AnimationTokens.duration(AnimationTokens.quickSettings, config)
                    val width by animateDpAsState(if (expanded) viewportWidth - GlassTokens.screenInset * 2 else 250.dp, tween(duration, easing = AnimationTokens.easing(config)), label = "settingsWidth")
                    val height by animateDpAsState(if (expanded) viewportHeight - GlassTokens.screenInset * 2 else 280.dp, tween(duration, easing = AnimationTokens.easing(config)), label = "settingsHeight")
                    Box(Modifier.width(width).height(height)
                        .glassSurface(config, stronger = expanded).testTag(if (expanded) "full-settings" else "quick-settings")) {
                        CompositionLocalProvider(LocalRemoteInputEnabled provides settingsVisible) {
                            if (expanded) AnimatedContent(
                                targetState = page,
                                transitionSpec = {
                                    val millis = AnimationTokens.duration(AnimationTokens.submenu, config)
                                    fadeIn(tween(millis)) togetherWith fadeOut(tween(millis))
                                },
                                label = "settingsPage",
                            ) { displayedPage ->
                                val outgoingFocus = remember { FocusRequester() }
                                CompositionLocalProvider(LocalRemoteInputEnabled provides (settingsVisible && displayedPage == page)) {
                                    FullSettings(displayedPage, apps, config, callbacks,
                                        if (displayedPage == page) overlayFocus else outgoingFocus,
                                        onPage = { page = it }, onClose = { panel = Panel.HOME }, onSearch = ::openSearch, music = music)
                                }
                            } else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Schnelleinstellungen", Modifier.padding(start = 8.dp, bottom = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                MenuButton("Hintergrund", Modifier.focusRequester(overlayFocus)) { page = SettingsPage.WALLPAPER; panel = Panel.FULL }
                                MenuButton("Animationen") { page = SettingsPage.ANIMATIONS; panel = Panel.FULL }
                                MenuButton("Netzwerk", onClick = callbacks.onOpenNetwork)
                                MenuButton("Alle Einstellungen") { page = SettingsPage.MAIN; panel = Panel.FULL }
                            }
                        }
                    }
                }
                if (panel == Panel.CONTEXT) {
                    val app = apps.find { it.id == contextId }
                    if (app == null) LaunchedEffect(Unit) { panel = Panel.HOME }
                    else Column(Modifier.align(Alignment.BottomStart)
                        .padding(start = selectedX.coerceIn(28f, (viewportWidth.value - 270f).coerceAtLeast(28f)).dp, bottom = GlassTokens.aboveDock)
                        .width(245.dp).glassSurface(config, stronger = true).padding(14.dp).testTag("app-context"),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(app.label, Modifier.padding(start = 8.dp, bottom = 4.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MenuButton("Verschieben", Modifier.focusRequester(overlayFocus)) {
                            movingOrder = visibleApps.map { it.id }; movingId = app.id; panel = Panel.HOME
                        }
                        MenuButton(if (app.id in config.pinned) "Markierung lösen" else "Anheften") {
                            callbacks.onConfigChange { it.copy(pinned = if (app.id in it.pinned) it.pinned - app.id else it.pinned + app.id) }; restoreHome()
                        }
                        MenuButton("Ausblenden") { callbacks.onConfigChange { it.copy(hidden = it.hidden + app.id) }; restoreHome() }
                        MenuButton("App-Informationen") { callbacks.onAppInfo(app); restoreHome() }
                        MenuButton(if (app.isSystem) "System-App geschützt" else "Deinstallieren …", enabled = !app.isSystem) {
                            callbacks.onUninstall(app); restoreHome()
                        }
                    }
                }
                if (panel == Panel.CALENDAR) CalendarOverlay(overlayFocus, onClose = ::closeCalendar)
                if (panel == Panel.WEATHER) Column(Modifier.align(Alignment.Center).width(360.dp).glassSurface(config, stronger = true).padding(24.dp)) {
                    Text("Wetter · Offline", fontSize = 22.sp)
                    Text("Es ist noch keine Wetterquelle eingerichtet.", Modifier.padding(vertical = 16.dp), fontSize = 13.sp)
                    MenuButton("Schließen", Modifier.focusRequester(overlayFocus)) { panel = Panel.HOME }
                }
                if (panel == Panel.SEARCH) AppSearchOverlay(baseApps, overlayFocus,
                    onLaunch = { app -> selectedId = app.id; panel = Panel.HOME; launching = app },
                    onClose = { panel = searchOrigin })
                }
                launching?.let { app -> LaunchTransition(app, bounds[app.id]) { launchProgress.value } }
            }
        }
    }
}

internal fun Modifier.glassSurface(config: LauncherConfig, stronger: Boolean = false): Modifier =
    clip(RoundedCornerShape(GlassTokens.corner))
        .background(GlassTokens.tint.copy(alpha = config.glassOpacity.coerceIn(.1f, .6f) * .8f + if (stronger) .04f else 0f))
        .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(GlassTokens.corner))

@Composable
internal fun MenuButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    RemoteButton(label, modifier.fillMaxWidth(), enabled = enabled, onClick = onClick) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = if (enabled) .95f else .4f), maxLines = 2)
    }
}
