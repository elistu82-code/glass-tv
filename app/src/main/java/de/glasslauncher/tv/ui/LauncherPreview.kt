package de.glasslauncher.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherCallbacks
import de.glasslauncher.tv.core.LauncherConfig

/** Preview data never enters production discovery or persistent configuration. */
@Preview(name = "Glass TV · remote dashboard", widthDp = 960, heightDp = 540)
@Composable
private fun LauncherPreview() {
    var config by remember { mutableStateOf(LauncherConfig(reducedMotion = true)) }
    val apps = remember {
        listOf("Mediathek", "Musik", "Filme", "Fotos", "Spiele", "Bibliothek").mapIndexed { index, label ->
            InstalledApp("preview.app$index/.Main", "preview.app$index", label)
        }
    }
    LauncherScreen(apps, config, LauncherCallbacks(
        onConfigChange = { config = it(config) }, onLaunch = {}, onAppInfo = {}, onUninstall = {},
        onImportWallpaper = {}, onOpenNetwork = {}, onStockHome = {}, onExportConfig = {}, onImportConfig = {},
    )) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF102E2B), Color(0xFF769182)))))
    }
}
