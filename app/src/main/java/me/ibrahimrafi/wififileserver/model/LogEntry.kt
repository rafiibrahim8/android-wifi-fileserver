package me.ibrahimrafi.wififileserver.model

data class LogEntry(
    val timestampMs: Long,
    val clientIp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val bytes: Long,
    val durationMs: Long,
)
