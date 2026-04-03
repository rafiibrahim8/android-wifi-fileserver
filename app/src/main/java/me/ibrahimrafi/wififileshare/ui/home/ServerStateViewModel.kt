package me.ibrahimrafi.wififileshare.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.ibrahimrafi.wififileshare.model.LogEntry
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import me.ibrahimrafi.wififileshare.model.TransferItem

class ServerStateViewModel : ViewModel() {
    val isRunning: LiveData<Boolean> = ServerStateStore.isRunning
    val url: LiveData<String> = ServerStateStore.url
    val networkName: LiveData<String> = ServerStateStore.networkName
    val transfers: LiveData<List<TransferItem>> = ServerStateStore.transfers
    val logs: LiveData<List<LogEntry>> = ServerStateStore.logs
}
