package me.ibrahimrafi.wififileshare.ui.transfers

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import me.ibrahimrafi.wififileshare.model.TransferItem

class TransferLogViewModel : ViewModel() {
    val transfers: LiveData<List<TransferItem>> = ServerStateStore.transfers

    fun clearTransfers() = ServerStateStore.clearTransfers()
    fun clearLogs() = ServerStateStore.clearLogs()
    fun removeTransfer(id: String) = ServerStateStore.removeTransfer(id)
}
