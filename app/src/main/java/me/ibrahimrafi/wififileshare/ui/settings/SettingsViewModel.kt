package me.ibrahimrafi.wififileshare.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.ibrahimrafi.wififileshare.model.ServerStateStore

class SettingsViewModel : ViewModel() {
    val isRunning: LiveData<Boolean> = ServerStateStore.isRunning
    val hasPendingRestartNotice: LiveData<Boolean> = ServerStateStore.hasPendingRestartNotice

    fun markSettingsChangedWhileRunning() = ServerStateStore.markSettingsChangedWhileRunning()
}
