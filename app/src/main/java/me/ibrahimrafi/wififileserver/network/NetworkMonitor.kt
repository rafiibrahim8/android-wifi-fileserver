package me.ibrahimrafi.wififileserver.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkMonitor(context: Context, private val onIpLost: () -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var boundIp: String? = null

    private val recheckRunnable = Runnable { checkBoundIp() }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            scheduleRecheck()
        }
        override fun onAvailable(network: Network) {
            scheduleRecheck()
        }
    }

    fun start(boundIp: String) {
        this.boundIp = boundIp.takeUnless { it.isBlank() || it == "0.0.0.0" }
        cm?.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        handler.removeCallbacks(recheckRunnable)
        runCatching { cm?.unregisterNetworkCallback(callback) }
        boundIp = null
    }

    private fun scheduleRecheck() {
        handler.removeCallbacks(recheckRunnable)
        handler.postDelayed(recheckRunnable, RECHECK_DELAY_MS)
    }

    private fun checkBoundIp() {
        val target = boundIp
        val stillReachable = if (target != null) {
            isIpPresentOnAnyInterface(target)
        } else {
            hasNonLoopbackIpv4()
        }
        if (!stillReachable) {
            Timber.i("Bound IP %s no longer present after network change, stopping", target ?: "(none)")
            onIpLost()
        } else {
            Timber.d("Network changed but bound IP %s still present, server kept running", target ?: "(any)")
        }
    }

    private fun isIpPresentOnAnyInterface(targetIp: String): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().any { iface ->
            iface.isUp && iface.inetAddresses.toList().any { addr ->
                !addr.isLoopbackAddress && addr.hostAddress == targetIp
            }
        }
    }.getOrDefault(false)

    private fun hasNonLoopbackIpv4(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().any { iface ->
            iface.isUp && iface.inetAddresses.toList().any { addr ->
                addr is Inet4Address && !addr.isLoopbackAddress
            }
        }
    }.getOrDefault(false)

    companion object {
        private const val RECHECK_DELAY_MS = 1500L
    }
}
