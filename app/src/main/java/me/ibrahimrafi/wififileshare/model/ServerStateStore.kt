package me.ibrahimrafi.wififileshare.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.ArrayDeque

object ServerStateStore {
    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _url = MutableLiveData(ServerConfig.defaultLocalUrl())
    val url: LiveData<String> = _url

    private val _transfers = MutableLiveData<List<TransferItem>>(emptyList())
    val transfers: LiveData<List<TransferItem>> = _transfers

    private val _logs = MutableLiveData<List<LogEntry>>(emptyList())
    val logs: LiveData<List<LogEntry>> = _logs

    private const val MAX_LOGS = 500
    private val logDeque = ArrayDeque<LogEntry>()

    fun setRunning(running: Boolean) {
        _isRunning.postValue(running)
    }

    fun setUrl(url: String) {
        _url.postValue(url)
    }

    fun upsertTransfer(item: TransferItem) {
        val list = _transfers.value.orEmpty().toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val previous = list[index]
            val merged = item.copy(
                transferredBytes = maxOf(item.transferredBytes, previous.transferredBytes),
                speedBps = when {
                    item.speedBps > 0L -> item.speedBps
                    previous.speedBps > 0L && item.status == TransferStatus.ACTIVE -> previous.speedBps
                    else -> item.speedBps
                },
            )
            list[index] = merged
        } else {
            list.add(0, item)
        }
        _transfers.postValue(list)
    }

    fun clearTransfers() {
        _transfers.postValue(emptyList())
    }

    fun removeTransfer(id: String) {
        val updated = _transfers.value.orEmpty().filterNot { it.id == id }
        _transfers.postValue(updated)
    }

    fun addLog(entry: LogEntry) {
        if (logDeque.size >= MAX_LOGS) {
            logDeque.removeFirst()
        }
        logDeque.addLast(entry)
        _logs.postValue(logDeque.toList().asReversed())
    }

    fun clearLogs() {
        logDeque.clear()
        _logs.postValue(emptyList())
    }
}
