package de.glasslauncher.tv.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

internal data class LaunchFrame(
    val bounds: Rect, val cornerRadius: Float, val opacity: Float,
    val iconSize: Float, val iconCenterY: Float, val iconWidth: Float,
) {
    // Inset conservatively: never clip the rounded/antialiased edge or a translucent frame.
    val opaqueInterior: Rect? get() = if (opacity < 1f) null else
        bounds.deflate(cornerRadius).takeIf { it.width > 0f && it.height > 0f }
}

internal fun launchFrame(source: Rect?, viewport: Size, progress: Float, density: Float): LaunchFrame {
    val p = progress.coerceIn(0f, 1f)
    val start = source ?: Rect(viewport.width * .42f, viewport.height * .65f, viewport.width * .58f, viewport.height * .8f)
    val zoom = 1f + .06f * (p / .22f).coerceIn(0f, 1f)
    val expanded = Rect(start.center - Offset(start.width * zoom / 2, start.height * zoom / 2), Size(start.width * zoom, start.height * zoom))
    val t = ((p - .22f) / .78f).coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * t
    return LaunchFrame(
        Rect(mix(expanded.left, 0f), mix(expanded.top, 0f), mix(expanded.right, viewport.width), mix(expanded.bottom, viewport.height)),
        16f * density * (1 - t), (p / .22f).coerceIn(0f, 1f),
        mix(GlassTokens.appIconHeight.value * density * zoom, 112f * density),
        mix(expanded.top + (GlassTokens.appTilePadding.value + GlassTokens.appIconHeight.value/2) * density * zoom, viewport.height / 2),
        mix((start.width - 20f*density)*zoom,112f*density),
    )
}
