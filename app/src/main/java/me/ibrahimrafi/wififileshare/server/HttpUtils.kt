package me.ibrahimrafi.wififileshare.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.webkit.MimeTypeMap
import java.io.InputStream
import java.net.URLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

private val timeFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
}

fun formatTime(epochMs: Long): String {
    val formatter = timeFormatter.get() ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).also {
        timeFormatter.set(it)
    }
    return formatter.format(Date(epochMs))
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun percentEncodePath(path: String): String {
    return path.split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}

fun percentEncodeFileName(fileName: String): String {
    val sanitized = fileName.replace('\r', '_').replace('\n', '_')
    return URLEncoder.encode(sanitized, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

fun parseRangeHeader(header: String, size: Long): Pair<Long, Long>? {
    if (size <= 0L) return null
    val cleaned = header.trim()
    if (!cleaned.startsWith("bytes=")) return null
    val value = cleaned.removePrefix("bytes=").trim()
    if (value.isEmpty() || value.contains(',')) return null // single-range only

    val dashIndex = value.indexOf('-')
    if (dashIndex < 0) return null

    val startPart = value.substring(0, dashIndex).trim()
    val endPart = value.substring(dashIndex + 1).trim()

    if (startPart.isEmpty()) {
        // RFC 7233 suffix-byte-range-spec: bytes=-N (last N bytes)
        val suffixLength = endPart.toLongOrNull() ?: return null
        if (suffixLength <= 0L) return null
        val servedLength = minOf(suffixLength, size)
        val start = size - servedLength
        return start to (size - 1)
    }

    val start = startPart.toLongOrNull() ?: return null
    if (start !in 0 until size) return null

    if (endPart.isEmpty()) {
        return start to (size - 1)
    }

    val end = endPart.toLongOrNull() ?: return null
    if (end < start) return null
    return start to minOf(end, size - 1)
}

data class ContentRange(val start: Long, val end: Long, val total: Long)

fun parseContentRange(header: String?): ContentRange? {
    if (header.isNullOrBlank()) return null
    if (!header.startsWith("bytes ")) return null
    val parts = header.removePrefix("bytes ").split('/', limit = 2)
    if (parts.size != 2) return null
    val range = parts[0].split('-', limit = 2)
    if (range.size != 2) return null
    val start = range[0].toLongOrNull() ?: return null
    val end = range[1].toLongOrNull() ?: return null
    val total = parts[1].toLongOrNull() ?: return null
    if (start !in 0 until total || end < start || end >= total) return null
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
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    val activeNetwork = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
    if (capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
        val ipAddress = linkProperties?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress
        if (ipAddress != null) return ipAddress
    }

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    @Suppress("DEPRECATION")
    val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
    return if (ipInt == 0) "0.0.0.0" else String.format(
        Locale.US,
        "%d.%d.%d.%d",
        ipInt and 0xff,
        ipInt shr 8 and 0xff,
        ipInt shr 16 and 0xff,
        ipInt shr 24 and 0xff
    )
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
