package de.glasslauncher.tv.wallpaper

import android.view.TextureView
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.ui.AnimationTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WallpaperEngine(config: LauncherConfig, fullSettings: Boolean, musicActive: Boolean = false) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val key="${config.theme}|${config.wallpaperPath}|${config.wallpaperVideo}"
    val initial=if(fullSettings) WallpaperMemory.cached(key,true) else if(musicActive) WallpaperMemory.cachedMusic(key) else WallpaperMemory.cached(key)
    val target by produceState(initial,key,fullSettings,musicActive) {
        val normal=WallpaperMemory.image(key) { loadWallpaperImage(config) }
        value=when { fullSettings->WallpaperMemory.blurred(key,normal);musicActive->WallpaperMemory.musicBlurred(key,normal);else->normal }
    }
    var presented by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xFF142C29)).then(if(musicActive) Modifier.testTag("music-blurred-background") else Modifier)) {
        if (!presented) {
            WallpaperStill(target)
            SideEffect { if (target != null) presented = true }
        } else Crossfade(
            targetState = target,
            animationSpec = tween(AnimationTokens.duration(if (fullSettings) AnimationTokens.home else if (musicActive) AnimationTokens.music else AnimationTokens.wallpaper, config), easing = AnimationTokens.easing(config)),
            label = "Wallpaper",
        ) { frame ->
            WallpaperStill(frame)
        }
        if (config.wallpaperVideo && config.wallpaperPath != null) {
            key(config.wallpaperPath) { VideoSurface(config.wallpaperPath, WallpaperPolicy.shouldPlay(resumed, fullSettings || musicActive, true)) }
        }
        if (musicActive) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.56f),Color.Black.copy(alpha=.5f),Color.Black.copy(alpha=.68f)))))
        if (fullSettings) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)))
    }
}

@Composable
private fun WallpaperStill(frame: Bitmap?) {
    frame?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxSize().testTag("wallpaper-still"),contentScale=ContentScale.Crop) }
}

@Composable
private fun VideoSurface(path: String, active: Boolean) {
    var state by remember(path) { mutableStateOf(VideoPlaybackState.RELEASED) }
    val controller = remember(path) { VideoPlaybackController(path) { state = it } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(active)
    DisposableEffect(controller, lifecycle) {
        // Release synchronously: a stopped composition may not get another frame to dispose.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.setActive(false)
                Lifecycle.Event.ON_RESUME -> controller.setActive(currentActive)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.dispose() }
    }
    DisposableEffect(controller, active) {
        controller.setActive(active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { controller.setActive(false) }
    }
    if (active && state != VideoPlaybackState.ERROR) AndroidView(
        factory = { context -> TextureView(context).also(controller::attach) },
        modifier = Modifier.fillMaxSize().testTag("wallpaper-video"),
        onRelease = controller::detach,
    )
}
