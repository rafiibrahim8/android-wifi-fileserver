package me.ibrahimrafi.wififileshare

import android.app.Application
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import timber.log.Timber

class WiFiFileShareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        ServerStateStore.init(this)
    }
}
