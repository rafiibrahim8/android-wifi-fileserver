package me.ibrahimrafi.wififileshare.storage

import androidx.documentfile.provider.DocumentFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class DocumentTreeCache(private val root: DocumentFile) {
    private val cache = ConcurrentHashMap<String, List<DocumentFile>>()
    private val timestamps = ConcurrentHashMap<String, Long>()
    private val ttlMs = 5_000L

    fun listChildren(path: String): List<DocumentFile> {
        val now = System.currentTimeMillis()
        val cached = cache[path]
        if (cached != null && now - (timestamps[path] ?: 0L) < ttlMs) {
            return cached
        }
        val dir = resolvePath(path) ?: return emptyList()
        val children = dir.listFiles().toList()
        cache[path] = children
        timestamps[path] = now
        return children
    }

    fun resolvePath(path: String): DocumentFile? {
        if (path.isBlank() || path == "/") return root
        var current: DocumentFile = root
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8.name())
            val next = current.findFile(decoded) ?: return null
            current = next
        }
        return current
    }

    fun invalidate(path: String) {
        cache.remove(path)
        timestamps.remove(path)
    }
}
