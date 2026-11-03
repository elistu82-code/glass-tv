package de.glasslauncher.tv

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.doOnPreDraw
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import de.glasslauncher.tv.core.LauncherCallbacks
import de.glasslauncher.tv.data.ConfigCodec
import de.glasslauncher.tv.system.stockHomeIntent
import de.glasslauncher.tv.system.NetworkStatus
import de.glasslauncher.tv.system.LocalFileExchange
import de.glasslauncher.tv.ui.LauncherScreen
import de.glasslauncher.tv.wallpaper.WallpaperEngine
import de.glasslauncher.tv.wallpaper.copyBounded
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var fullyDrawnReported = false
    private var createdAt = 0L
    private var homeRequest by mutableIntStateOf(0)
    private val model: LauncherViewModel by viewModels()
    // Fire OS can deny shell access to external app storage; run-as reliably reaches this debug bridge.
    private val exchange by lazy { LocalFileExchange(File(filesDir, "exchange")) }
    private val mediaExchange by lazy { externalMediaDirs.firstOrNull()?.let { LocalFileExchange(File(it,"exchange")) } }

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importWallpaper(uri)
    }
    private fun importWallpaper(uri: Uri) {
        lifecycleScope.launch {
            try { model.importWallpaper(uri); message("Hintergrund gespeichert") }
            catch (e: Exception) { message("Import abgelehnt: ${e.message ?: "Datei nicht lesbar"}") }
        }
    }
    private val configPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importConfig(uri)
    }
    private fun importConfig(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input)
                        val bytes = ByteArrayOutputStream()
                        copyBounded(input, bytes, ConfigCodec.MAX_BYTES.toLong())
                        bytes.toString(Charsets.UTF_8.name())
                    }
                }
                model.restore(text)
                message("Einstellungen wiederhergestellt · eigene Hintergrunddateien separat importieren")
            } catch (_: Exception) { message("Sicherung abgelehnt · bestehende Einstellungen bleiben erhalten") }
        }
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val snapshot = model.config.copy(wallpaperPath = null, wallpaperVideo = false)
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val json = ConfigCodec.encode(snapshot)
                        contentResolver.openOutputStream(uri, "wt").use { output ->
                            requireNotNull(output)
                            output.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    message("Sicherung exportiert")
                } catch (_: Exception) { message("Export nicht möglich") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        createdAt = android.os.SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val error = model.error
            LaunchedEffect(error) { error?.let { message(it); model.clearError() } }
            run {
                val music by model.music.collectAsStateWithLifecycle()
                LaunchedEffect(model.config.musicMode,music.connected,music.isPlaying,music.artwork) {
                    de.glasslauncher.tv.media.MediaTrace.event("ui.state", "mode=${model.config.musicMode} connected=${music.connected} playing=${music.isPlaying} art=${music.artwork!=null}")
                }
                LauncherScreen(model.apps, model.config, LauncherCallbacks(
                    onConfigChange = model::update,
                    onLaunch = { if (!model.appRepository.launch(it)) message("App nicht verfügbar") },
                    onAppInfo = { if (!model.appRepository.details(it)) message("App-Informationen nicht verfügbar") },
                    onUninstall = { if (!model.appRepository.uninstall(it)) message("Deinstallation für diese App nicht verfügbar") },
                    onImportWallpaper = ::chooseWallpaper,
                    onOpenNetwork = ::network,
                    onStockHome = ::stockHome,
                    onExportConfig = ::exportConfig,
                    onImportConfig = ::chooseConfig,
                    onMusicAccess = ::musicAccess,
                    onMusicPrevious = { model.previousTrack() },
                    onMusicPlayPause = { model.playPause() },
                    onMusicNext = { model.nextTrack() },
                    onOpenMusic = { lifecycleScope.launch {
                        if(!model.appRepository.launchPackage("com.spotify.tv.android")) message("Spotify ist nicht installiert oder kann nicht geöffnet werden")
                    } },
                ), music = music, homeRequest = homeRequest, appsLoading = !model.ready || model.loadingApps,
                    onDockReady = {
                        if (model.ready && !fullyDrawnReported) {
                            fullyDrawnReported = true
                            window.decorView.post {
                                reportFullyDrawn()
                                if (BuildConfig.DEBUG) {
                                    val dock=android.os.SystemClock.elapsedRealtime()
                                    android.util.Log.i("GlassPerf", "Glass.dockReady ${dock-createdAt}ms")
                                    lifecycleScope.launch(Dispatchers.IO) { runCatching {
                                        File(cacheDir,"startup-last.json").writeText(com.google.gson.JsonObject().apply { addProperty("pid",android.os.Process.myPid()); addProperty("created",createdAt); addProperty("dockReady",dock) }.toString())
                                    } }
                                }
                            }
                        }
                    }) { fullSettings ->
                    if (model.ready) WallpaperEngine(model.config, fullSettings, musicActive=model.config.musicMode && music.connected)
                    else Box(Modifier.fillMaxSize().background(Color(0xFF142C29)))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequest++
    }

    override fun onResume() {
        super.onResume(); model.foreground(); model.refreshApps()
        window.decorView.doOnPreDraw { window.decorView.post { model.startOptionalMedia() } }
    }
    override fun onStop() { model.background(); super.onStop() }

    private fun stockHome() {
        try { stockHomeIntent(this)?.let(::startActivity) ?: message("System-Startbildschirm nicht verfügbar") }
        catch (_: ActivityNotFoundException) { message("System-Startbildschirm nicht verfügbar") }
        catch (_: SecurityException) { message("System-Startbildschirm nicht freigegeben") }
    }

    private fun musicAccess() {
        AlertDialog.Builder(this).setTitle("Optionaler Spotify-Zugriff")
            .setMessage("Android benötigt Benachrichtigungszugriff für fremde MediaSessions. Diese Systemfreigabe umfasst auch Benachrichtigungen anderer Apps. Glass TV liest deren Inhalte nicht und verarbeitet ausschließlich Spotify-MediaSessions. Die Freigabe ist freiwillig; Fire OS bietet den Dialog möglicherweise nicht an. Ohne Zugriff bleiben Musiksteuerung und Albumcover deaktiviert.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Systemdialog öffnen") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (_: ActivityNotFoundException) { message("Fire OS stellt den Freigabedialog nicht bereit. Keine Berechtigung wurde erteilt.") }
                catch (_: SecurityException) { message("Fire OS erlaubt diesen Freigabedialog nicht.") }
            }.show()
    }

    private fun network() {
        AlertDialog.Builder(this).setTitle("Netzwerkstatus").setMessage(NetworkStatus.summary(this))
            .setNegativeButton("Schließen", null)
            .setPositiveButton("Netzwerkeinstellungen") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                catch (_: ActivityNotFoundException) { message("Systemeinstellungen nicht verfügbar") }
                catch (_: SecurityException) { message("Systemeinstellungen nicht freigegeben") }
            }.show()
    }

    private fun chooseWallpaper() {
        try { wallpaperPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "video/mp4")) }
        catch (_: ActivityNotFoundException) { chooseLocal("Hintergrund importieren", setOf("jpg", "jpeg", "png", "webp", "mp4"), ::importWallpaper) }
        catch (_: SecurityException) { message("Dateiauswahl nicht freigegeben") }
    }

    private fun chooseConfig() {
        try { configPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        catch (_: ActivityNotFoundException) { chooseLocal("Sicherung importieren", setOf("json"), ::importConfig) }
        catch (_: SecurityException) { message("Dateiauswahl nicht freigegeben") }
    }

    private fun chooseLocal(title: String, extensions: Set<String>, accept: (Uri) -> Unit) {
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { (listOfNotNull(mediaExchange,exchange).flatMap { it.listImports(extensions) }) }
                val dialog = AlertDialog.Builder(this@MainActivity).setTitle(title).setNegativeButton("Abbrechen", null)
                if (files.isEmpty()) {
                    dialog.setMessage("Keine passenden Dateien im Glass-TV-Importordner. Am Rechner mit dem Glass-TV-Transferwerkzeug bereitstellen:\n\npython3 tools/transfer.py --device FIRE_TV_ENDPOINT import DATEI\n\nAnleitung: docs/FILE_TRANSFER.md")
                } else {
                    dialog.setItems(files.map { it.name }.toTypedArray()) { _, index ->
                        try { accept(FileProvider.getUriForFile(this@MainActivity, "$packageName.files", files[index])) }
                        catch (_: Exception) { message("Datei nicht verfügbar") }
                    }
                }
                dialog.show()
            } catch (_: Exception) { message("Importordner nicht verfügbar") }
        }
    }

    private fun exportConfig() {
        try { exportPicker.launch("glass-tv-settings.json") }
        catch (_: ActivityNotFoundException) {
            val snapshot = ConfigCodec.encode(model.config)
            lifecycleScope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { (if (BuildConfig.DEBUG) exchange else mediaExchange ?: exchange).exportConfig(snapshot) }
                    AlertDialog.Builder(this@MainActivity).setTitle("Sicherung gespeichert")
                        .setMessage("${file.name}\n\nAuch app-eigene Austauschordner können bei Deinstallation gelöscht werden. Jetzt mit dem Glass-TV-Transferwerkzeug auf den Rechner sichern:\n\npython3 tools/transfer.py --device FIRE_TV_ENDPOINT backup artifacts/backups\n\nAnleitung: docs/FILE_TRANSFER.md")
                        .setPositiveButton("Schließen", null).show()
                } catch (_: Exception) { message("Export nicht möglich") }
            }
        }
        catch (_: SecurityException) { message("Dateiauswahl nicht freigegeben") }
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
