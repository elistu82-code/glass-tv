package de.glasslauncher.tv.media

import android.service.notification.NotificationListenerService

/** Optional system binding only. Notification contents are neither inspected nor retained. */
class GlassMediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaTrace.event("listener.connected")
        MediaListenerEvents.notifyConnection(true)
    }

    override fun onListenerDisconnected() {
        MediaTrace.event("listener.disconnected")
        MediaListenerEvents.notifyConnection(false)
        super.onListenerDisconnected()
    }
}

internal object MediaListenerEvents {
    val listeners = mutableSetOf<(Boolean) -> Unit>()
    fun notifyConnection(connected: Boolean) = listeners.toList().forEach { it(connected) }
}
