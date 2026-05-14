package me.ibrahimrafi.wififileserver.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import me.ibrahimrafi.wififileserver.model.Direction
import me.ibrahimrafi.wififileserver.model.ServerStateStore
import me.ibrahimrafi.wififileserver.model.TransferItem
import me.ibrahimrafi.wififileserver.model.TransferStatus

class HomeViewModel : ViewModel() {

    data class ActiveStat(val rateBps: Long, val count: Int)

    val isRunning: LiveData<Boolean> = ServerStateStore.isRunning
    val url: LiveData<String> = ServerStateStore.url
    val transfers: LiveData<List<TransferItem>> = ServerStateStore.transfers

    val outgoing: LiveData<ActiveStat> = transfers.map { all ->
        val active = all.filter { it.status == TransferStatus.ACTIVE && it.direction == Direction.DOWNLOAD }
        ActiveStat(active.sumOf { it.speedBps }, active.size)
    }

    val incoming: LiveData<ActiveStat> = transfers.map { all ->
        val active = all.filter { it.status == TransferStatus.ACTIVE && it.direction == Direction.UPLOAD }
        ActiveStat(active.sumOf { it.speedBps }, active.size)
    }
}
