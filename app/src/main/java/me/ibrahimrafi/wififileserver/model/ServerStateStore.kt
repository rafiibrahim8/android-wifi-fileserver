package me.ibrahimrafi.wififileserver.model

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped state store + lightweight persistence for transfer history.
 *
 * Backing types are [MutableStateFlow] so compound updates (upsertTransfer, addLog) are
 * atomic via [update]; public read APIs surface both Flow (preferred, for new code) and
 * LiveData (for existing fragments observing via .observe).
 */
object ServerStateStore {

    private const val MAX_TRANSFERS = 200
    private const val MAX_LOGS = 500
    private const val PERSIST_FILE = "transfers.json"
    private const val PERSIST_DEBOUNCE_MS = 1000L

    // --- public state --------------------------------------------------------

    private val _isRunning = MutableStateFlow(false)
    val isRunningFlow: StateFlow<Boolean> = _isRunning.asStateFlow()
    val isRunning: LiveData<Boolean> = _isRunning.asLiveData()

    private val _hasPendingRestartNotice = MutableStateFlow(false)
    val hasPendingRestartNoticeFlow: StateFlow<Boolean> = _hasPendingRestartNotice.asStateFlow()
    val hasPendingRestartNotice: LiveData<Boolean> = _hasPendingRestartNotice.asLiveData()

    private val _url = MutableStateFlow(ServerConfig.defaultLocalUrl())
    val urlFlow: StateFlow<String> = _url.asStateFlow()
    val url: LiveData<String> = _url.asLiveData()

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfersFlow: StateFlow<List<TransferItem>> = _transfers.asStateFlow()
    val transfers: LiveData<List<TransferItem>> = _transfers.asLiveData()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    val logs: LiveData<List<LogEntry>> = _logs.asLiveData()

    // --- persistence ---------------------------------------------------------

    private val appContext = AtomicReference<Context?>(null)
    private val persistExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ServerStateStore-persist").apply { isDaemon = true }
    }
    private val pendingSave = AtomicReference<ScheduledFuture<*>?>(null)

    fun init(context: Context) {
        if (!appContext.compareAndSet(null, context.applicationContext)) return
        loadPersistedTransfers()
    }

    // --- mutators ------------------------------------------------------------

    fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) _hasPendingRestartNotice.value = false
    }

    fun markSettingsChangedWhileRunning() {
        _hasPendingRestartNotice.value = true
    }

    fun setUrl(url: String) {
        _url.value = url
    }

    fun upsertTransfer(item: TransferItem) {
        _transfers.update { current ->
            val index = current.indexOfFirst { it.id == item.id }
            val merged = if (index >= 0) {
                val previous = current[index]
                item.copy(
                    transferredBytes = maxOf(item.transferredBytes, previous.transferredBytes),
                    speedBps = when {
                        item.speedBps > 0L -> item.speedBps
                        previous.speedBps > 0L && item.status == TransferStatus.ACTIVE -> previous.speedBps
                        else -> item.speedBps
                    },
                )
            } else {
                item
            }
            val next = ArrayList<TransferItem>(current.size + 1)
            if (index >= 0) {
                next.addAll(current)
                next[index] = merged
            } else {
                next.add(merged)
                next.addAll(current)
            }
            if (next.size > MAX_TRANSFERS) cap(next) else next
        }
        scheduleSave()
    }

    fun clearTransfers() {
        _transfers.value = emptyList()
        scheduleSave()
    }

    fun removeTransfer(id: String) {
        _transfers.update { current -> current.filterNot { it.id == id } }
        scheduleSave()
    }

    fun addLog(entry: LogEntry) {
        _logs.update { current ->
            // Newest first; cap at MAX_LOGS.
            val next = ArrayList<LogEntry>(minOf(current.size + 1, MAX_LOGS))
            next.add(entry)
            for (i in 0 until minOf(current.size, MAX_LOGS - 1)) next.add(current[i])
            next
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    @Volatile private var cancelUploadHandler: ((String) -> Boolean)? = null

    fun setCancelUploadHandler(handler: ((String) -> Boolean)?) {
        cancelUploadHandler = handler
    }

    fun cancelUpload(path: String): Boolean = cancelUploadHandler?.invoke(path) ?: false

    private fun cap(items: List<TransferItem>): List<TransferItem> {
        val keep = ArrayList<TransferItem>(MAX_TRANSFERS)
        keep.addAll(items.asSequence().filter { it.status == TransferStatus.ACTIVE })
        for (entry in items) {
            if (keep.size >= MAX_TRANSFERS) break
            if (entry.status != TransferStatus.ACTIVE) keep.add(entry)
        }
        return keep
    }

    // --- persistence implementation -----------------------------------------

    private fun scheduleSave() {
        val ctx = appContext.get() ?: return
        val previous = pendingSave.getAndSet(
            persistExecutor.schedule(
                { runCatching { persistTransfers(ctx) } },
                PERSIST_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS,
            ),
        )
        previous?.cancel(false)
    }

    private fun persistTransfers(ctx: Context) {
        val snapshot = _transfers.value
        val array = JSONArray()
        for (item in snapshot) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("relativePath", item.relativePath)
                put("direction", item.direction.name)
                put("totalBytes", item.totalBytes)
                put("transferredBytes", item.transferredBytes)
                put("speedBps", item.speedBps)
                put("clientIp", item.clientIp)
                put("status", item.status.name)
            }
            array.put(obj)
        }
        val file = File(ctx.filesDir, PERSIST_FILE)
        val tmp = File(ctx.filesDir, "$PERSIST_FILE.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun loadPersistedTransfers() {
        val ctx = appContext.get() ?: return
        val file = File(ctx.filesDir, PERSIST_FILE)
        if (!file.exists()) return
        val parsed = runCatching {
            val text = file.readText()
            if (text.isBlank()) return@runCatching emptyList<TransferItem>()
            val array = JSONArray(text)
            val list = ArrayList<TransferItem>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val status = runCatching { TransferStatus.valueOf(o.optString("status")) }
                    .getOrDefault(TransferStatus.FAILED)
                val recovered = if (status == TransferStatus.ACTIVE) TransferStatus.FAILED else status
                val direction = runCatching { Direction.valueOf(o.optString("direction")) }
                    .getOrDefault(Direction.DOWNLOAD)
                list += TransferItem(
                    id = o.optString("id"),
                    fileName = o.optString("fileName"),
                    relativePath = o.optString("relativePath"),
                    direction = direction,
                    totalBytes = o.optLong("totalBytes"),
                    transferredBytes = o.optLong("transferredBytes"),
                    speedBps = 0L,
                    clientIp = o.optString("clientIp"),
                    status = recovered,
                )
            }
            list
        }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) _transfers.value = parsed
    }
}
