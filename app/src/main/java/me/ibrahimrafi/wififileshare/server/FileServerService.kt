package me.ibrahimrafi.wififileshare.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import fi.iki.elonen.NanoHTTPD
import me.ibrahimrafi.wififileshare.MainActivity
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import me.ibrahimrafi.wififileshare.model.TransferStatus
import me.ibrahimrafi.wififileshare.network.NetworkMonitor
import me.ibrahimrafi.wififileshare.network.NsdAdvertiser
import me.ibrahimrafi.wififileshare.storage.ServerPreferences

class FileServerService : LifecycleService() {
    private var server: WiFiFileServer? = null
    private lateinit var prefs: ServerPreferences
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var advertiser: NsdAdvertiser

    private val notifier = Handler(Looper.getMainLooper())
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
            Toast.makeText(this, it.message ?: "Could not start server", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        runCatching { instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }.onFailure {
            Toast.makeText(this, it.message ?: "Could not start server", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        server = instance
        networkMonitor.start()
        advertiser.start(config.port)
        val url = "http://${localIpAddress(this)}:${config.port}"
        ServerStateStore.setRunning(true)
        ServerStateStore.setUrl(url)
        copyUrlToClipboard(url)
    }

    private fun stopServer() {
        runCatching { server?.stop() }
        server = null
        runCatching { networkMonitor.stop() }
        runCatching { advertiser.stop() }
        notifier.removeCallbacks(updateNotificationRunnable)
        ServerStateStore.setRunning(false)
        ServerStateStore.clearTransfers()
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
        val text = if (activeTransfers > 0) "$url · $activeTransfers active transfers" else "$url · No active transfers"

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
        clipboard.setPrimaryClip(ClipData.newPlainText("WiFi File Share URL", url))
        Toast.makeText(this, getString(R.string.url_copied), Toast.LENGTH_SHORT).show()
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

        const val ACTION_START = "me.ibrahimrafi.wififileshare.action.START"
        const val ACTION_STOP = "me.ibrahimrafi.wififileshare.action.STOP"

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
