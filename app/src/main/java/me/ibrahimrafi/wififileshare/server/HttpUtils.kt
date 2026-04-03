package me.ibrahimrafi.wififileshare.server

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import java.io.InputStream
import java.net.URLEncoder
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatTime(epochMs: Long): String {
    return timeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

fun percentEncodePath(path: String): String {
    return path.split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}

fun percentEncodeFileName(fileName: String): String {
    return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

fun parseRangeHeader(header: String, size: Long): Pair<Long, Long>? {
    val cleaned = header.removePrefix("bytes=")
    val pieces = cleaned.split('-', limit = 2)
    if (pieces.size != 2) return null
    val start = pieces[0].toLongOrNull() ?: 0L
    val end = if (pieces[1].isBlank()) size - 1 else pieces[1].toLongOrNull() ?: return null
    if (start < 0 || end < start || start >= size) return null
    return start to max(start, minOf(end, size - 1))
}

data class ContentRange(val start: Long, val end: Long, val total: Long)

fun parseContentRange(header: String?): ContentRange? {
    if (header.isNullOrBlank()) return null
    val parts = header.removePrefix("bytes ").split('/', limit = 2)
    if (parts.size != 2) return null
    val range = parts[0].split('-', limit = 2)
    if (range.size != 2) return null
    val start = range[0].toLongOrNull() ?: return null
    val end = range[1].toLongOrNull() ?: return null
    val total = parts[1].toLongOrNull() ?: return null
    if (start < 0 || end < start || total <= end) return null
    return ContentRange(start, end, total)
}

fun InputStream.skipFully(bytes: Long) {
    var remain = bytes
    while (remain > 0L) {
        val skipped = skip(remain)
        if (skipped <= 0L) {
            if (read() == -1) break
            remain--
        } else {
            remain -= skipped
        }
    }
}

fun localIpAddress(context: Context): String {
    val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val info = manager?.connectionInfo ?: return "0.0.0.0"
    return Formatter.formatIpAddress(info.ipAddress)
}

fun wifiSsid(context: Context): String {
    val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val ssid = manager?.connectionInfo?.ssid ?: return "Unknown"
    return ssid.trim('"')
}

fun resolveDownloadMimeType(fileName: String?, documentMimeType: String?): String {
    val extension = fileName
        ?.substringAfterLast('.', "")
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
    if (extension != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
    }

    fileName?.let { URLConnection.guessContentTypeFromName(it)?.let { guessed -> return guessed } }

    val normalizedDocType = documentMimeType?.trim()?.lowercase(Locale.ROOT)
    if (!normalizedDocType.isNullOrBlank() &&
        normalizedDocType != "application/octet-stream" &&
        normalizedDocType != "*/*"
    ) {
        return normalizedDocType
    }

    return "application/octet-stream"
}
