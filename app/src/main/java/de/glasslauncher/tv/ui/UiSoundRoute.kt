package de.glasslauncher.tv.ui

internal enum class UiSoundRoute { SILENT, NATIVE, MEDIA }
internal fun uiSoundRoute(effectsEnabled:Boolean,systemAudible:Boolean,mediaAudible:Boolean):UiSoundRoute = when {
    !effectsEnabled -> UiSoundRoute.SILENT
    systemAudible -> UiSoundRoute.NATIVE
    mediaAudible -> UiSoundRoute.MEDIA
    else -> UiSoundRoute.SILENT
}
