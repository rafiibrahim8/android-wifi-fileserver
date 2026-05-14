package me.ibrahimrafi.wififileserver.model

enum class Direction { DOWNLOAD, UPLOAD }

enum class TransferStatus { ACTIVE, COMPLETED, FAILED, CANCELLED }

data class TransferItem(
    val id: String,
    val fileName: String,
    val relativePath: String = "",
    val direction: Direction,
    val totalBytes: Long,
    val transferredBytes: Long,
    val speedBps: Long,
    val clientIp: String,
    val status: TransferStatus = TransferStatus.ACTIVE,
)
