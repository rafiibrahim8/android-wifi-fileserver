package me.ibrahimrafi.wififileshare.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import me.ibrahimrafi.wififileshare.model.ServerConfig

class ServerPreferences(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "server_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getConfig(): ServerConfig {
        val port = prefs.getString(KEY_PORT, "8080")?.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080
        val rootUri = prefs.getString(KEY_ROOT_URI, null)?.let(Uri::parse)
        return ServerConfig(
            port = port,
            anonymousAccess = prefs.getBoolean(KEY_ANON, true),
            userId = prefs.getString(KEY_USER, "") ?: "",
            password = securePrefs.getString(KEY_PASSWORD, "") ?: "",
            useSsl = prefs.getBoolean(KEY_SSL, false),
            readOnlyFileserver = prefs.getBoolean(KEY_READ_ONLY_FILESERVER, false),
            allowUploads = prefs.getBoolean(KEY_ALLOW_UPLOADS, true),
            allowZipDownload = prefs.getBoolean(KEY_ALLOW_ZIP, false),
            showHiddenFiles = prefs.getBoolean(KEY_SHOW_HIDDEN, false),
            maxConnections = prefs.getInt(KEY_MAX_CONNECTIONS, 10).coerceIn(1, 50),
            maxSpeedBps = parseSpeed(prefs.getString(KEY_MAX_SPEED, "off") ?: "off"),
            rootUri = rootUri,
        )
    }

    fun setRootUri(uri: Uri) {
        prefs.edit { putString(KEY_ROOT_URI, uri.toString()) }
    }

    fun setPassword(password: String) {
        securePrefs.edit { putString(KEY_PASSWORD, password) }
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
        const val KEY_SSL = "use_ssl"
        const val KEY_ROOT_URI = "root_folder_uri"
        const val KEY_READ_ONLY_FILESERVER = "read_only_fileserver"
        const val KEY_ALLOW_UPLOADS = "allow_uploads"
        const val KEY_ALLOW_ZIP = "allow_zip"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_MAX_CONNECTIONS = "max_connections"
        const val KEY_MAX_SPEED = "max_speed"
    }
}
