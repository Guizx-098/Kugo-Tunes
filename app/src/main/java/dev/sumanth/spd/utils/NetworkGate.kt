package dev.sumanth.spd.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Whether downloads may run right now. */
object NetworkGate {
    const val OK = 0
    const val NO_INTERNET = 1
    const val NEEDS_WIFI = 2

    fun state(context: Context, wifiOnly: Boolean): Int {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return OK
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return NO_INTERNET
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NO_INTERNET
        // "Not metered" = Wi-Fi / Ethernet without a data limit (mobile data is metered)
        if (wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return NEEDS_WIFI
        return OK
    }
}
