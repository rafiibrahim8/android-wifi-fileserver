package me.ibrahimrafi.wififileshare.model

import android.net.Uri

data class ServerConfig(
    val port: Int = 8080,
    val anonymousAccess: Boolean = true,
    val userId: String = "",
    val password: String = "",
    val useSsl: Boolean = false,
    val readOnlyFileserver: Boolean = false,
    val allowUploads: Boolean = true,
    val allowZipDownload: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val maxConnections: Int = 10,
    val maxSpeedBps: Long = 0L,
    val uploadSizeLimitBytes: Long = 4L * 1024L * 1024L * 1024L,
    val rootUri: Uri? = null,
)
