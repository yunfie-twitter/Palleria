package com.yunfie.illustia.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidConnectivity(
    private val context: Context,
) : Connectivity {
    private val manager: ConnectivityManager?
        get() = context.getSystemService(ConnectivityManager::class.java)

    private fun activeCapabilities(): NetworkCapabilities? = manager?.let { it.getNetworkCapabilities(it.activeNetwork) }

    override fun isWifiConnected(): Boolean = activeCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    override fun isOffline(): Boolean = manager?.activeNetwork == null

    override fun isMetered(): Boolean = activeCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
}
