package me.ibrahimrafi.wififileshare.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

class NetworkMonitor(context: Context, private val onLost: () -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            onLost()
        }
    }

    fun start() {
        cm?.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }
}
