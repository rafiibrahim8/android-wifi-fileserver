package me.ibrahimrafi.wififileserver.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class NsdAdvertiser(context: Context) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun start(port: Int) {
        if (nsdManager == null || listener != null) return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "WiFiFileServer"
            serviceType = "_http._tcp."
            setPort(port)
        }
        listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        val l = listener ?: return
        runCatching { nsdManager?.unregisterService(l) }
        listener = null
    }
}
