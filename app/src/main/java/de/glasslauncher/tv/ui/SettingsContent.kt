package de.glasslauncher.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherCallbacks
import de.glasslauncher.tv.core.LauncherConfig
import java.util.Locale
import de.glasslauncher.tv.media.SpotifyState
import de.glasslauncher.tv.data.SystemAppPolicy

@Composable
internal fun FullSettings(
    page: SettingsPage,
    apps: List<InstalledApp>,
    config: LauncherConfig,
    callbacks: LauncherCallbacks,
    firstFocus: FocusRequester,
    onPage: (SettingsPage) -> Unit,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    music: SpotifyState = SpotifyState(),
) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("GLASS TV", fontSize = 10.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = .6f))
                Text(when (page) {
                    SettingsPage.MAIN -> "Einstellungen"
                    SettingsPage.WALLPAPER -> "Hintergrund"
                    SettingsPage.ANIMATIONS -> "Animationen & Glas"
                    SettingsPage.APPS -> "Apps verwalten"
                    SettingsPage.MUSIC -> "Musik & Atmosphäre"
                    SettingsPage.SYSTEM -> "System & Sicherung"
                }, fontSize = 25.sp, fontWeight = FontWeight.Light)
            }
            Text("Zurück · eine Ebene höher", fontSize = 11.sp, color = Color.White.copy(alpha = .6f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (page) {
                SettingsPage.MAIN -> {
                    MenuButton("Hintergrund", Modifier.focusRequester(firstFocus)) { onPage(SettingsPage.WALLPAPER) }
                    MenuButton("Animationen & Glas") { onPage(SettingsPage.ANIMATIONS) }
                    MenuButton("Apps verwalten") { onPage(SettingsPage.APPS) }
                    MenuButton("Musik & Atmosphäre") { onPage(SettingsPage.MUSIC) }
                    MenuButton("System & Sicherung") { onPage(SettingsPage.SYSTEM) }
                    MenuButton("Zurück zum Homescreen", onClick = onClose)
                }
                SettingsPage.WALLPAPER -> {
                    Note("Originale Farbwelten als Platzhalter. App-Reihenfolge und Glas bleiben beim Wechsel erhalten.")
                    listOf("forest" to "Wald · sanftes Dunkelgrün", "zen" to "Zen · ruhige Sandtöne", "liquid" to "Liquid Glass · kühles Blau", "neon" to "Neon · Violett & Nacht").forEachIndexed { index, (key, label) ->
                        MenuButton((if (config.theme == key && config.wallpaperPath == null) "✓  " else "") + label,
                            if (index == 0) Modifier.focusRequester(firstFocus) else Modifier) {
                            callbacks.onConfigChange { it.copy(theme = key, wallpaperPath = null, wallpaperVideo = false) }
                        }
                    }
                    MenuButton("Lokales Bild oder MP4 importieren …", onClick = callbacks.onImportWallpaper)
                    if (config.wallpaperPath != null) Note("Eigener Hintergrund aktiv · ${if (config.wallpaperVideo) "Video" else "Bild"}")
                    Note("Lokale Dateiauswahl benötigt einen kompatiblen Dokumentanbieter auf Fire OS.")
                }
                SettingsPage.ANIMATIONS -> {
                    MenuButton("Geschwindigkeit: ${String.format(Locale.GERMAN, "%.1f", config.animationSpeed)}×", Modifier.focusRequester(firstFocus)) {
                        val values = listOf(.5f, .75f, 1f, 1.5f, 2f)
                        callbacks.onConfigChange { it.copy(animationSpeed = values[(values.indexOf(it.animationSpeed) + 1).mod(values.size)]) }
                    }
                    MenuButton("Bewegung reduzieren: ${yesNo(config.reducedMotion)}") { callbacks.onConfigChange { it.copy(reducedMotion = !it.reducedMotion) } }
                    MenuButton("Verlauf: ${if (config.easing == "linear") "Linear" else "Sanft"}") { callbacks.onConfigChange { it.copy(easing = if (it.easing == "linear") "smooth" else "linear") } }
                    MenuButton("Glasintensität: ${(config.glassOpacity * 100).toInt()} %") {
                        val values = listOf(.14f, .22f, .34f, .46f)
                        callbacks.onConfigChange { it.copy(glassOpacity = values[(values.indexOf(it.glassOpacity) + 1).mod(values.size)]) }
                    }
                    MenuButton("UI-Sounds: ${yesNo(config.uiSounds)}") { callbacks.onConfigChange { it.copy(uiSounds = !it.uiSounds) } }
                    MenuButton("Soundintensität: ${(config.uiSoundIntensity * 100).toInt()} %", enabled = config.uiSounds) {
                        val levels = listOf(.25f, .5f, .75f, 1f)
                        callbacks.onConfigChange { it.copy(uiSoundIntensity = levels[(levels.indexOf(it.uiSoundIntensity) + 1).mod(levels.size)]) }
                    }
                    Note("Bei stummen Systemtönen laufen UI-Sounds über die Medienlautstärke. Stummschaltung und System-Soundschalter gelten; TV-Lautstärke bleibt unverändert.")
                    Note("Fokus 300 ms · Popup 450 ms · Untermenü 350 ms · Hintergrund 1200 ms. Änderungen werden direkt gespeichert.")
                    Note("Starke Unschärfe wird als vorbereitetes Standbild berechnet, um den Fire TV zu entlasten.")
                }
                SettingsPage.APPS -> {
                    val hidden = apps.filter { SystemAppPolicy.isHidden(it, config) }.sortedBy { it.label.lowercase(Locale.ROOT) }
                    MenuButton("Apps suchen", Modifier.focusRequester(firstFocus), onClick = onSearch)
                    MenuButton("Alle Apps wieder einblenden (${hidden.size})") {
                        callbacks.onConfigChange { it.copy(hidden = emptySet(), shownSystemApps = it.shownSystemApps + apps.filter(SystemAppPolicy::hiddenByDefault).map { app -> app.id }) }
                    }
                    hidden.forEach { app -> MenuButton("${app.label} einblenden") { callbacks.onConfigChange { it.copy(hidden = it.hidden - app.id, shownSystemApps = it.shownSystemApps + app.id) } } }
                    if (hidden.isEmpty()) Note("Keine installierten Apps ausgeblendet.")
                    MenuButton("Interne Systemtools wieder standardmäßig ausblenden") { callbacks.onConfigChange { it.copy(shownSystemApps = emptySet()) } }
                    Note("Nur bekannte technische Systemwerkzeuge werden standardmäßig gefiltert. Normale Streaming-/Amazon-Apps und Benutzerinstallationen bleiben erhalten. Kein Paket wird verändert.")
                    Note("Auf dem Homescreen OK gedrückt halten: verschieben, anheften, ausblenden, App-Informationen oder offizielle Deinstallation. System-Apps sind geschützt.")
                }
                SettingsPage.MUSIC -> {
                    MenuButton("Albumcover-Modus: ${yesNo(config.musicMode)}", Modifier.focusRequester(firstFocus)) {
                        callbacks.onConfigChange { it.copy(musicMode = !it.musicMode) }
                    }
                    Note(if (config.musicMode) music.status else "Spotify-Modus ausgeschaltet · Albumcover-Modus oben einschalten.")
                    MenuButton("Optionalen Spotify-Zugriff erklären …", onClick = callbacks.onMusicAccess)
                    MenuButton("Songtitel & Interpret anzeigen: ${yesNo(config.showMusicInfo)}") { callbacks.onConfigChange { it.copy(showMusicInfo = !it.showMusicInfo) } }
                    Note("Steuerung und Albumcover erscheinen nur bei einer zugänglichen Spotify-Sitzung. Ohne Verbindung bleibt der gewählte Hintergrund aktiv.")
                    MenuButton("Simulierter Visualizer: ${yesNo(config.simulatedVisualizer)}") {
                        callbacks.onConfigChange { it.copy(simulatedVisualizer = !it.simulatedVisualizer) }
                    }
                    Note("Die Simulation reagiert nicht auf Musik und verwendet weder Mikrofon noch Audioaufnahme. Bei reduzierter Bewegung bleibt sie statisch.")
                }
                SettingsPage.SYSTEM -> {
                    MenuButton("Netzwerkstatus & Systemeinstellungen", Modifier.focusRequester(firstFocus), onClick = callbacks.onOpenNetwork)
                    MenuButton("Konfiguration exportieren …", onClick = callbacks.onExportConfig)
                    MenuButton("Konfiguration wiederherstellen …", onClick = callbacks.onImportConfig)
                    Note("Sicherungen enthalten nur Launcher-Einstellungen. Eigene Hintergrunddateien werden separat verwaltet.")
                    MenuButton("Zum Fire-TV-Startbildschirm", onClick = callbacks.onStockHome)
                    Note("Eine Home-Tasten-Umleitung wird separat verwaltet. Glass TV verändert keine Amazon-Systempakete. Wetter und Kalender-Synchronisation sind noch nicht eingerichtet.")
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp,
        lineHeight = 18.sp, color = Color.White.copy(alpha = .72f))
}

private fun yesNo(value: Boolean) = if (value) "An" else "Aus"
