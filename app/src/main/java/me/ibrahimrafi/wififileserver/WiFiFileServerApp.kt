package me.ibrahimrafi.wififileserver

import android.app.Application
import fi.iki.elonen.NanoHTTPD
import me.ibrahimrafi.wififileserver.model.ServerStateStore
import timber.log.Timber
import java.util.logging.Level
import java.util.logging.Logger

class WiFiFileServerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // NanoHTTPD logs every client-side disconnect at SEVERE (e.g. "Could not send
        // response to the client" with a Connection-reset trace). Those fire on every
        // upload cancel and download abort, which is normal — bump the floor so they
        // don't drown out actual server errors (port conflicts etc.).
        Logger.getLogger(NanoHTTPD::class.java.name).level = Level.WARNING
        ServerStateStore.init(this)
    }
}
