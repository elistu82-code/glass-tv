package de.glasslauncher.tv.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import de.glasslauncher.tv.core.LauncherConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.withContext

internal val LocalUiSounds=staticCompositionLocalOf<UiSoundFeedback?> { null }

@Composable
internal fun rememberUiSounds(config: LauncherConfig): UiSoundFeedback {
    val injected=LocalUiSounds.current
    val context=LocalContext.current.applicationContext
    val view=LocalView.current
    val current by rememberUpdatedState(config)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val events=remember { Channel<Pair<UiSound,Long>>(8,BufferOverflow.DROP_OLDEST) }
    val feedback=injected ?: remember { UiSoundFeedback(SystemClock::uptimeMillis) { events.trySend(it to SystemClock.uptimeMillis()) } }
    SideEffect { feedback.enabled=config.uiSounds }
    // Our manual focus/key handling owns feedback. Suppress framework sounds on this Compose view only.
    DisposableEffect(view) {
        val original=view.isSoundEffectsEnabled
        view.isSoundEffectsEnabled=false
        onDispose { view.isSoundEffectsEnabled=original; events.close() }
    }
    LaunchedEffect(events,injected) {
        if (injected==null) {
            withFrameNanos { } // Do not initialize even the tiny audio pool ahead of the first frame.
            withContext(Dispatchers.IO) {
                val player=UiSoundPlayer(context)
                try {
                    player.prepare()
                    for ((sound,at) in events) if (current.uiSounds &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && SystemClock.uptimeMillis()-at<=150) {
                        player.play(sound,current.uiSoundIntensity)
                    }
                } finally { player.close() }
            }
        }
    }
    return feedback
}

internal fun UiSoundFeedback.key(event: KeyEvent) {
    if(event.type!=KeyEventType.KeyDown) return
    val sound=when(event.key) {
        Key.DirectionLeft -> UiSound.LEFT; Key.DirectionRight -> UiSound.RIGHT
        Key.DirectionUp -> UiSound.UP; Key.DirectionDown -> UiSound.DOWN
        else -> null
    }
    if(sound==null) cancelNavigation() else direction(sound)
}
