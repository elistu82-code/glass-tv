package de.glasslauncher.tv.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper

/** Supplies vetted controllers; transport/metadata remain real Android MediaController operations. */
internal interface SpotifySessions {
    fun register(onChanged: () -> Unit)
    fun active(): List<MediaController>
    fun unregister()
}

internal class AndroidSpotifySessions(context: Context) : SpotifySessions {
    private val manager = context.applicationContext.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context.applicationContext, GlassMediaListener::class.java)
    private var listener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    override fun register(onChanged: () -> Unit) {
        MediaTrace.event("sessions.register", "componentMatches=${component.className == GlassMediaListener::class.java.name}")
        val service = checkNotNull(manager) { "MediaSessionManager unavailable" }
        val callback = MediaSessionManager.OnActiveSessionsChangedListener { onChanged() }
        service.addOnActiveSessionsChangedListener(callback, component, Handler(Looper.getMainLooper()))
        listener = callback
    }
    override fun active(): List<MediaController> {
        val all=manager?.getActiveSessions(component).orEmpty()
        val spotify=all.filter { isSpotifyPackage(it.packageName) }
        MediaTrace.event("sessions.read", "total=${all.size} spotify=${spotify.size} tv=${spotify.count { it.packageName == "com.spotify.tv.android" }}")
        return spotify
    }
    override fun unregister() {
        val callback = listener ?: return
        listener = null
        manager?.removeOnActiveSessionsChangedListener(callback)
    }
}
