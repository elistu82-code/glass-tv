package de.glasslauncher.tv.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import de.glasslauncher.tv.core.LauncherConfig

object GlassTokens {
    val tint = Color(0xFFF3F4F6)
    val corner = 24.dp
    val screenInset = 28.dp
    val appIconHeight = 70.dp
    val appTilePadding = 4.dp
    val appWidth = 182.dp
    val appHeight = 94.dp
    const val dockWidthFraction = .82f
    val rowBottom = 104.dp
    val dockVerticalPadding = 8.dp
    val appSpacing = 14.dp
    val dockHorizontalPadding = 8.dp
    fun dockViewportWidth(available: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
        val slots=((available-dockHorizontalPadding*2+appSpacing)/(appWidth+appSpacing)).toInt().coerceAtLeast(1)
        return minOf(available,appWidth*slots+appSpacing*(slots-1)+dockHorizontalPadding*2)
    }
    val appTileBackground = Color(0xFF27313D)
    val aboveDock = rowBottom + appHeight + dockVerticalPadding*2 + 16.dp
}

object AnimationTokens {
    const val focus = 300
    const val quickSettings = 450
    const val submenu = 350
    const val homeReturn = 220
    const val home = 600
    const val appLaunch = 550
    const val wallpaper = 1200
    const val music = 1500
    fun duration(base: Int, config: LauncherConfig): Int =
        if (config.reducedMotion) 0 else (base / config.animationSpeed.coerceIn(.5f, 2f)).toInt()
    fun easing(config: LauncherConfig): Easing =
        if (config.easing == "linear") LinearEasing else CubicBezierEasing(.22f, .61f, .36f, 1f)
}

internal val LocalGlassConfig = staticCompositionLocalOf { LauncherConfig() }
internal val LocalRemoteInputEnabled = staticCompositionLocalOf { true }
