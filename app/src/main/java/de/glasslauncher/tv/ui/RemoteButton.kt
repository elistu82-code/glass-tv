package de.glasslauncher.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun RemoteButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val sounds = LocalUiSounds.current
    val soundToken = remember { Any() }
    val config = LocalGlassConfig.current
    val interactive = enabled && LocalRemoteInputEnabled.current
    val scope = rememberCoroutineScope()
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val background = animateColorAsState(
        if (focused) Color.White.copy(alpha = .20f) else Color.White.copy(alpha = .025f),
        tween(AnimationTokens.duration(AnimationTokens.focus, config), easing = AnimationTokens.easing(config)),
        label = "focusMaterial",
    )
    DisposableEffect(Unit) { onDispose { holdJob?.cancel() } }
    Row(
        modifier
            .clip(shape)
            .drawBehind { drawRect(background.value) }
            .border(if (focused) 1.5.dp else .5.dp,
                Color.White.copy(alpha = if (focused) .8f else .08f), shape)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                if (!interactive) disabled()
                onClick {
                    if (interactive) { sounds?.confirm(); currentClick() }
                    interactive
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) { sounds?.focused(soundToken); onFocused() } else {
                    holdJob?.cancel()
                    pressed = false
                }
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                val select = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    code == AndroidKeyEvent.KEYCODE_ENTER || code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                when {
                    !select -> false
                    !interactive -> true
                    event.type == KeyEventType.KeyDown -> {
                        if (!pressed && event.nativeKeyEvent.repeatCount == 0) {
                            pressed = true
                            held = false
                            holdJob?.cancel()
                            if (currentLongClick != null) holdJob = scope.launch {
                                delay(timeout)
                                if (pressed) {
                                    held = true
                                    sounds?.confirm()
                                    currentLongClick?.invoke()
                                }
                            }
                        }
                        true
                    }
                    event.type == KeyEventType.KeyUp -> {
                        holdJob?.cancel()
                        val click = pressed && !held
                        pressed = false
                        if (click) { sounds?.confirm(); currentClick() }
                        true
                    }
                    else -> true
                }
            }
            .focusable(interactive)
            .padding(contentPadding),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content,
    )
}
