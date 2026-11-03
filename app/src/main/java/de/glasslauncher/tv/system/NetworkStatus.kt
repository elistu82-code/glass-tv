package de.glasslauncher.tv.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkStatus {
    fun summary(context: Context): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return "Offline · kein aktives Netzwerk"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "Netzwerkstatus nicht verfügbar"
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Netzwerk"
        }
        val validation = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "vom System geprüft" else "Internetzugang nicht bestätigt"
        return "$transport verbunden · $validation\nGlass TV sendet keine Netzwerkanfragen."
    }
}
