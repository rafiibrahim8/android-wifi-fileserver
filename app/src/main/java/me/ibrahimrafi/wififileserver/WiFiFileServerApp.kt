package me.ibrahimrafi.wififileserver

import android.app.Application
import me.ibrahimrafi.wififileserver.model.ServerStateStore
import timber.log.Timber

class WiFiFileServerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        ServerStateStore.init(this)
    }
}
