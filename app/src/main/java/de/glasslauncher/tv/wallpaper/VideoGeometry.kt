package de.glasslauncher.tv.wallpaper

import kotlin.math.max

internal data class VideoCrop(val scaleX: Float, val scaleY: Float)

/** TextureView initially stretches content to its bounds; correct that stretch before cropping. */
internal fun videoCenterCrop(viewWidth: Int, viewHeight: Int, videoWidth: Int, videoHeight: Int): VideoCrop {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return VideoCrop(1f, 1f)
    val scale = max(viewWidth.toFloat() / videoWidth, viewHeight.toFloat() / videoHeight)
    return VideoCrop(videoWidth * scale / viewWidth, videoHeight * scale / viewHeight)
}
