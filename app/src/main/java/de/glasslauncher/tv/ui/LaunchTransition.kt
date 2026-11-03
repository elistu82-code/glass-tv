package de.glasslauncher.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.glasslauncher.tv.core.InstalledApp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun LaunchTransition(app: InstalledApp, source: Rect?, progress: () -> Float) {
    val icon = rememberAppIcon(app)?.asImageBitmap()
    Canvas(Modifier.fillMaxSize().testTag("launch-transition")) {
        val frame = launchFrame(source, size, progress(), density)
        val rect = frame.bounds
        drawRoundRect(Color(0xFF545B5A).copy(alpha = frame.opacity), rect.topLeft, rect.size, CornerRadius(frame.cornerRadius))
        icon?.let {
            val max = frame.iconSize
            val centerY = frame.iconCenterY
            val scale = min(frame.iconWidth / it.width, max / it.height)
            val dst = IntSize((it.width * scale).roundToInt().coerceAtLeast(1), (it.height * scale).roundToInt().coerceAtLeast(1))
            drawImage(it, dstOffset = IntOffset((rect.center.x - dst.width / 2).roundToInt(), (centerY - dst.height / 2).roundToInt()), dstSize = dst, alpha = frame.opacity)
        }
    }
}

/** Skip painting pixels that the opaque launch surface will immediately cover anyway. */
internal fun Modifier.behindLaunch(active: Boolean, source: Rect?, progress: () -> Float): Modifier = drawWithContent {
    val interior = if (active) launchFrame(source, size, progress(), density).opaqueInterior else null
    if (interior == null) drawContent()
    else clipRect(interior.left, interior.top, interior.right, interior.bottom, ClipOp.Difference) { this@drawWithContent.drawContent() }
}
