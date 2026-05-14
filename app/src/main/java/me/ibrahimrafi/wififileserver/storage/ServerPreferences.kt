package me.ibrahimrafi.wififileserver.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import me.ibrahimrafi.wififileserver.model.ServerConfig

class ServerPreferences(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun getConfig(): ServerConfig {
        val defaultPort = ServerConfig.DEFAULT_PORT
        val port = prefs.getString(KEY_PORT, defaultPort.toString())?.toIntOrNull()?.coerceIn(1024, 65535) ?: defaultPort
        val rootUri = prefs.getString(KEY_ROOT_URI, null)?.let(Uri::parse)
        val idleMin = prefs.getString(KEY_IDLE_TIMEOUT_MIN, "0")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        return ServerConfig(
            port = port,
            anonymousAccess = prefs.getBoolean(KEY_ANON, true),
            userId = prefs.getString(KEY_USER, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            readOnlyFileserver = prefs.getBoolean(KEY_READ_ONLY_FILESERVER, false),
            dropBoxMode = prefs.getBoolean(KEY_DROP_BOX_MODE, false),
            allowUploads = prefs.getBoolean(KEY_ALLOW_UPLOADS, true),
            allowZipDownload = prefs.getBoolean(KEY_ALLOW_ZIP, false),
            showHiddenFiles = prefs.getBoolean(KEY_SHOW_HIDDEN, false),
            maxSpeedBps = parseSpeed(prefs.getString(KEY_MAX_SPEED, "off") ?: "off"),
            idleTimeoutMs = idleMin * 60_000L,
            rootUri = rootUri,
        )
    }

    fun setRootUri(uri: Uri) {
        prefs.edit { putString(KEY_ROOT_URI, uri.toString()) }
    }

    private fun parseSpeed(value: String): Long {
        return when (value) {
            "1m" -> 1L * 1024L * 1024L
            "5m" -> 5L * 1024L * 1024L
            "10m" -> 10L * 1024L * 1024L
            "unlimited" -> Long.MAX_VALUE
            else -> 0L
        }
    }

    companion object {
        const val KEY_PORT = "port"
        const val KEY_ANON = "anonymous_access"
        const val KEY_USER = "user_id"
        const val KEY_PASSWORD = "password"
        const val KEY_ROOT_URI = "root_folder_uri"
        const val KEY_READ_ONLY_FILESERVER = "read_only_fileserver"
        const val KEY_ALLOW_UPLOADS = "allow_uploads"
        const val KEY_ALLOW_ZIP = "allow_zip"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_MAX_SPEED = "max_speed"
        const val KEY_IDLE_TIMEOUT_MIN = "idle_timeout_minutes"
        const val KEY_DROP_BOX_MODE = "drop_box_mode"
    }
}
