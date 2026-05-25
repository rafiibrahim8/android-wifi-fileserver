package me.ibrahimrafi.wififileserver.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import fi.iki.elonen.NanoHTTPD
import me.ibrahimrafi.wififileserver.MainActivity
import me.ibrahimrafi.wififileserver.R
import me.ibrahimrafi.wififileserver.model.ServerStateStore
import me.ibrahimrafi.wififileserver.model.TransferStatus
import me.ibrahimrafi.wififileserver.network.NetworkMonitor
import me.ibrahimrafi.wififileserver.network.NsdAdvertiser
import me.ibrahimrafi.wififileserver.storage.ServerPreferences
import me.ibrahimrafi.wififileserver.widget.ServerWidgetProvider
import timber.log.Timber

class FileServerService : LifecycleService() {
    private var server: WiFiFileServer? = null
    private lateinit var prefs: ServerPreferences
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var advertiser: NsdAdvertiser

    private val notifier = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var idleTimeoutMs: Long = 0L
    private val idleChecker = object : Runnable {
        override fun run() {
            val srv = server ?: return
            val timeout = idleTimeoutMs
            if (timeout > 0L && System.currentTimeMillis() - srv.lastActivity >= timeout) {
                Timber.i("Auto-stopping after %d ms of idle", timeout)
                stopServer()
                stopSelf()
                return
            }
            notifier.postDelayed(this, IDLE_CHECK_INTERVAL_MS)
        }
    }
    private val updateNotificationRunnable = object : Runnable {
        override fun run() {
            if (server != null) {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification())
                notifier.postDelayed(this, 2_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = ServerPreferences(this)
        advertiser = NsdAdvertiser(this)
        networkMonitor = NetworkMonitor(this) {
            stopServer()
            stopSelf()
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startServerIfNeeded()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        notifier.removeCallbacks(updateNotificationRunnable)
        notifier.post(updateNotificationRunnable)
        return START_STICKY
    }

    private fun startServerIfNeeded() {
        if (server != null) return
        val config = prefs.getConfig()
        val rootUri = config.rootUri
        if (rootUri == null) {
            Toast.makeText(this, getString(R.string.folder_not_selected), Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val instance = runCatching { WiFiFileServer(this, config) }.getOrElse {
            Timber.e(it, "Failed to construct WiFiFileServer")
            Toast.makeText(this, it.message ?: getString(R.string.server_start_failed), Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        runCatching { instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }.onFailure {
            Timber.e(it, "WiFiFileServer.start() failed on port ${config.port}")
            Toast.makeText(this, it.message ?: getString(R.string.server_start_failed), Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        server = instance
        val ip = localIpAddress(this)
        networkMonitor.start(ip)
        advertiser.start(config.port)
        acquireLocks()
        idleTimeoutMs = config.idleTimeoutMs
        if (idleTimeoutMs > 0L) {
            notifier.removeCallbacks(idleChecker)
            notifier.postDelayed(idleChecker, IDLE_CHECK_INTERVAL_MS)
        }
        val url = "http://$ip:${config.port}"
        ServerStateStore.setRunning(true)
        ServerStateStore.setUrl(url)
        ServerWidgetProvider.updateAll(this)
        copyUrlToClipboard(url)
        Thread {
            runCatching { instance.sweepStalePartials() }
                .onSuccess { Timber.i("Swept %d stale .part files", it) }
                .onFailure { Timber.w(it, "Failed to sweep .part files") }
        }.start()
    }

    private fun stopServer() {
        runCatching { server?.stop() }
        server = null
        runCatching { networkMonitor.stop() }
        runCatching { advertiser.stop() }
        releaseLocks()
        notifier.removeCallbacks(updateNotificationRunnable)
        notifier.removeCallbacks(idleChecker)
        ServerStateStore.setRunning(false)
        // Don't wipe history; cancel anything that was still in flight so the list reflects reality.
        ServerStateStore.transfersFlow.value
            .filter { it.status == TransferStatus.ACTIVE }
            .forEach { ServerStateStore.upsertTransfer(it.copy(speedBps = 0L, status = TransferStatus.CANCELLED)) }
        ServerWidgetProvider.updateAll(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    private fun buildNotification(): Notification {
        val config = prefs.getConfig()
        val url = "http://${localIpAddress(this)}:${config.port}"
        val activeTransfers = ServerStateStore.transfers.value.orEmpty().count { it.status == TransferStatus.ACTIVE }
        val text = if (activeTransfers > 0) {
            getString(R.string.notif_active_transfers, url, activeTransfers)
        } else {
            getString(R.string.notif_no_active_transfers, url)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, FileServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    private fun copyUrlToClipboard(url: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label_url), url))
        Toast.makeText(this, getString(R.string.url_copied), Toast.LENGTH_SHORT).show()
    }

    private fun acquireLocks() {
        runCatching {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wifiserver:server").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Timber.w(it, "Failed to acquire wake lock") }

        runCatching {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL
            }
            wifiLock = wm.createWifiLock(mode, "wifiserver:server").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Timber.w(it, "Failed to acquire Wi-Fi lock") }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_description)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "file_server_channel"
        private const val NOTIFICATION_ID = 301
        private const val IDLE_CHECK_INTERVAL_MS = 30_000L

        const val ACTION_START = "me.ibrahimrafi.wififileserver.action.START"
        const val ACTION_STOP = "me.ibrahimrafi.wififileserver.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, FileServerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FileServerService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
