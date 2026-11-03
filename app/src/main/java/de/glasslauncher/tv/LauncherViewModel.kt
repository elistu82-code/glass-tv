package de.glasslauncher.tv

import android.app.Application
import android.net.Uri
import de.glasslauncher.tv.media.SpotifySessionAdapter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherConfig
import de.glasslauncher.tv.data.AppRepository
import de.glasslauncher.tv.data.ConfigCoordinator
import de.glasslauncher.tv.data.ConfigRepository
import de.glasslauncher.tv.wallpaper.WallpaperStore
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val appRepository = AppRepository(application)
    private var media: SpotifySessionAdapter? = null
    private var foreground = false
    private var presented = false
    private val musicState = kotlinx.coroutines.flow.MutableStateFlow(de.glasslauncher.tv.media.SpotifyState())
    val music: kotlinx.coroutines.flow.StateFlow<de.glasslauncher.tv.media.SpotifyState> = musicState
    fun foreground() { foreground = true; syncMusic() }
    fun startOptionalMedia() { presented = true; syncMusic() }
    fun background() { foreground = false; media?.stop(); musicState.value = de.glasslauncher.tv.media.SpotifyState() }
    private fun syncMusic() {
        de.glasslauncher.tv.media.MediaTrace.event("vm.sync", "ready=$ready foreground=$foreground presented=$presented mode=${config.musicMode}")
        if (ready && foreground && presented && config.musicMode) {
            val adapter = media ?: SpotifySessionAdapter(getApplication<Application>()).also { created ->
                media = created
                viewModelScope.launch { created.state.collect { de.glasslauncher.tv.media.MediaTrace.distinct("vm.state", "connected=${it.connected} playing=${it.isPlaying} art=${it.artwork != null}"); musicState.value = it } }
            }
            adapter.start()
        } else media?.stop()
    }
    fun previousTrack() { media?.previous() }
    fun playPause() { media?.playPause() }
    fun nextTrack() { media?.next() }
    override fun onCleared() {
        media?.close()
        getApplication<Application>().unregisterReceiver(packageChanges)
        super.onCleared()
    }
    private val repository = ConfigRepository(application)
    private val wallpapers = WallpaperStore(application)
    private lateinit var changes: ConfigCoordinator
    private val initialized = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val catalogue = de.glasslauncher.tv.data.RefreshingCatalog { withContext(Dispatchers.IO) { appRepository.discover() } }
    private var discovery: Job? = null
    var loadingApps by mutableStateOf(true)
        private set
    private val packageChanges = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            catalogue.invalidate()
            intent.data?.schemeSpecificPart?.let(appRepository::invalidatePackage)
            if (foreground) refreshApps()
        }
    }
    var config by mutableStateOf(LauncherConfig())
        private set
    var apps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var importing by mutableStateOf(false)
        private set

    init {
        val filter=android.content.IntentFilter().apply {
            listOf(android.content.Intent.ACTION_PACKAGE_ADDED,android.content.Intent.ACTION_PACKAGE_REMOVED,
                android.content.Intent.ACTION_PACKAGE_CHANGED,android.content.Intent.ACTION_PACKAGE_REPLACED).forEach(::addAction)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(application,packageChanges,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            val cached=async(Dispatchers.IO) { appRepository.cachedDiscovery() }
            config = repository.load()
            changes = ConfigCoordinator(config) { repository.save(it) }
            apps = cached.await()
            ready = true
            initialized.complete(Unit)
            refreshApps()
            syncMusic()
            // Orphan cleanup remains after successful imports, not on the startup critical path.
        }
    }

    fun refreshApps() {
        if (discovery?.isActive == true) return
        discovery = viewModelScope.launch {
            var completed = false
            try {
                initialized.await()
                loadingApps = catalogue.isStale
                apps = catalogue.get()
                completed = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Apps konnten nicht aktualisiert werden" }
            finally {
                loadingApps = false
                discovery = null
                if (completed && catalogue.isStale && foreground) refreshApps()
            }
        }
    }

    fun update(change: (LauncherConfig) -> LauncherConfig) {
        viewModelScope.launch {
            try {
                initialized.await()
                config = changes.update(change)
                syncMusic()
            } catch (_: Exception) { error = "Einstellungen konnten nicht gespeichert werden" }
        }
    }

    suspend fun restore(json: String) {
        initialized.await()
        config = changes.restore(json)
        syncMusic()
    }

    suspend fun importWallpaper(uri: Uri) {
        initialized.await()
        if (importing) return
        importing = true
        try {
            val imported = wallpapers.import(uri)
            config = changes.update { it.copy(wallpaperPath = imported.path, wallpaperVideo = imported.video) }
            wallpapers.prune(config.wallpaperPath)
        } finally { importing = false }
    }

    fun clearError() { error = null }
}
