package de.glasslauncher.tv.wallpaper

object WallpaperPolicy {
    fun shouldPlay(resumed: Boolean, fullSettings: Boolean, video: Boolean): Boolean =
        resumed && !fullSettings && video
}
