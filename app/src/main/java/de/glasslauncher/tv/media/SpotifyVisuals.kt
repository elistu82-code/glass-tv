package de.glasslauncher.tv.media

import de.glasslauncher.tv.core.LauncherConfig

internal data class SpotifyVisuals(val visible: Boolean,val animate: Boolean)
internal fun spotifyVisuals(config: LauncherConfig,music: SpotifyState): SpotifyVisuals {
    val active=config.musicMode && music.connected
    val visible=config.simulatedVisualizer && active
    return SpotifyVisuals(visible,visible && music.isPlaying && !config.reducedMotion)
}
