package me.ibrahimrafi.wififileserver.model

import android.net.Uri

data class ServerConfig(
    val port: Int = DEFAULT_PORT,
    val anonymousAccess: Boolean = true,
    val userId: String = "",
    val password: String = "",
    val readOnlyFileserver: Boolean = false,
    val dropBoxMode: Boolean = false,
    val allowUploads: Boolean = true,
    val allowZipDownload: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val maxSpeedBps: Long = 0L,
    val uploadSizeLimitBytes: Long = 4L * 1024L * 1024L * 1024L,
    val idleTimeoutMs: Long = 0L,
    val rootUri: Uri? = null,
) {
    companion object {
        const val DEFAULT_PORT = 1200
        private const val DEFAULT_BIND_HOST = "0.0.0.0"

        fun defaultLocalUrl(): String = "http://$DEFAULT_BIND_HOST:$DEFAULT_PORT"
    }
}
