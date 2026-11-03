package de.glasslauncher.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun SimulatedVisualizer(modifier: Modifier = Modifier, animate: Boolean = true) {
    val config = LocalGlassConfig.current
    val phase = if (config.reducedMotion || !animate) 0f else {
        val transition = rememberInfiniteTransition(label = "simulatedVisualizer")
        val value by transition.animateFloat(0f, (PI * 2).toFloat(),
            infiniteRepeatable(tween(AnimationTokens.duration(AnimationTokens.music * 3, config), easing = LinearEasing), RepeatMode.Restart),
            label = "simulatedBars")
        value
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(200.dp).height(28.dp)) {
            val stride = size.width / 24
            repeat(24) { index ->
                val height = (0.18f + .68f * ((sin(phase + index * .71f) + 1) / 2)) * size.height
                drawRoundRect(Color.White.copy(alpha = .5f), Offset(index * stride, size.height - height), Size(stride * .45f, height), CornerRadius(3f, 3f))
            }
        }
        Text("Simulation · ohne Audioanalyse", fontSize = 9.sp, color = Color.White.copy(alpha = .55f))
    }
}
