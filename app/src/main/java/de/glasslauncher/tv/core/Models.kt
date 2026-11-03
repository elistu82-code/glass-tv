package de.glasslauncher.tv.core

import android.graphics.drawable.Drawable

data class InstalledApp(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val isSystem: Boolean = false,
    val iconRevision: String = "",
)

data class LauncherConfig(
    val version: Int = 1,
    val order: List<String> = emptyList(),
    val pinned: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val shownSystemApps: Set<String> = emptySet(),
    val theme: String = "forest",
    val wallpaperPath: String? = null,
    val wallpaperVideo: Boolean = false,
    val animationSpeed: Float = 1f,
    val reducedMotion: Boolean = false,
    val glassOpacity: Float = 0.22f,
    val easing: String = "smooth",
    val musicMode: Boolean = false,
    val showMusicInfo: Boolean = true,
    val simulatedVisualizer: Boolean = false,
    val uiSounds: Boolean = true,
    val uiSoundIntensity: Float = .5f,
)

data class LauncherCallbacks(
    val onConfigChange: ((LauncherConfig) -> LauncherConfig) -> Unit,
    val onLaunch: (InstalledApp) -> Unit,
    val onAppInfo: (InstalledApp) -> Unit,
    val onUninstall: (InstalledApp) -> Unit,
    val onImportWallpaper: () -> Unit,
    val onOpenNetwork: () -> Unit,
    val onStockHome: () -> Unit,
    val onExportConfig: () -> Unit,
    val onImportConfig: () -> Unit,
    val onMusicAccess: () -> Unit = {},
    val onMusicPrevious: () -> Unit = {},
    val onMusicPlayPause: () -> Unit = {},
    val onMusicNext: () -> Unit = {},
    val onOpenMusic: () -> Unit = {},
)
