package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherConfig

/** Exact technical-tool list, gated by SYSTEM flags. Never excludes an entire vendor. */
object SystemAppPolicy {
    val technicalPackages = setOf(
        "com.android.settings", "com.android.tv.settings", "com.google.android.tv.remote.service", "com.android.systemui", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.android.documentsui", "com.android.traceur",
        "com.android.development", "com.android.certinstaller", "com.android.shell",
        "com.amazon.tv.settings", "com.amazon.tv.settings.v2", "com.amazon.firetv.settings",
        "com.amazon.firetv.troubleshooting", "com.amazon.whasettings",
        "com.amazon.tv.earlyaccess", "com.amazon.ftv.profilepicker",
        "com.amazon.tv.launcher", "com.amazon.tv.systemui", "com.amazon.tv.notificationcenter",
        "com.amazon.device.software.ota", "com.amazon.device.software.ota.override",
        "com.amazon.device.logmanager", "com.amazon.logmanager", "com.amazon.tv.fw.metrics",
        "com.amazon.whisperlink.core.android",
    )
    fun hiddenByDefault(app: InstalledApp) = app.isSystem && app.packageName in technicalPackages
    fun isHidden(app: InstalledApp, config: LauncherConfig) = app.id in config.hidden ||
        (hiddenByDefault(app) && app.id !in config.shownSystemApps)
}
