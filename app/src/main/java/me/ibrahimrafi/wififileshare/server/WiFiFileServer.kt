package me.ibrahimrafi.wififileshare.server

import android.content.Context
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import me.ibrahimrafi.wififileshare.model.Direction
import me.ibrahimrafi.wififileshare.model.LogEntry
import me.ibrahimrafi.wififileshare.model.ServerConfig
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import me.ibrahimrafi.wififileshare.model.TransferItem
import me.ibrahimrafi.wififileshare.model.TransferStatus
import me.ibrahimrafi.wififileshare.storage.DocumentTreeCache
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WiFiFileServer(
    private val context: Context,
    private val config: ServerConfig,
) : NanoHTTPD(config.port) {

    private val root: DocumentFile = config.rootUri?.let { DocumentFile.fromTreeUri(context, it) }
        ?: throw IllegalStateException("No root folder selected")

    private val cache = DocumentTreeCache(root)
    private val tokenManager = AccessTokenManager()
    private val directoryHandler = DirectoryHandler(context, cache, config)
    private val downloadHandler = DownloadHandler(context, config.maxSpeedBps)
    private val uploadHandler = UploadHandler(context, root, config.uploadSizeLimitBytes)
    private val requestWindow = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val uploadTransferIds = ConcurrentHashMap<String, String>()
    private val webUiAssetToken = generateWebUiAssetToken()
    private val webUiAssetBasePath = "/.$webUiAssetToken"
    private val webUiStyleCssBytes = readAssetBytes("webui/style.css")
    private val webUiAppJsBytes = readAssetBytes("webui/app.js")
    private val webUiFaviconBytes = readAssetBytes("webui/favicon.ico")

    private val serverBase: String
        get() = "http://${localIpAddress(context)}:${config.port}"

    override fun serve(session: IHTTPSession): Response {
        val started = System.currentTimeMillis()
        val ip = session.remoteIpAddress ?: "unknown"

        val response = try {
            if (!allowRate(ip)) {
                newFixedLengthResponse(Response.Status.TOO_MANY_REQUESTS, MIME_PLAINTEXT, "Too many requests")
            } else if (hasTraversal(session.uri)) {
                newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid path")
            } else if (!authenticate(session)) {
                unauthorizedResponse()
            } else {
                route(session, ip)
            }
        } catch (t: Throwable) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, t.message ?: "Internal error")
        }

        val bytes = response.getHeader("Content-Length")?.toLongOrNull() ?: -1L
        ServerStateStore.addLog(
            LogEntry(
                timestampMs = started,
                clientIp = ip,
                method = session.method.name,
                path = session.uri,
                statusCode = response.status.requestStatus,
                bytes = bytes,
                durationMs = System.currentTimeMillis() - started,
            ),
        )
        return response
    }

    private fun route(session: IHTTPSession, ip: String): Response {
        val uri = session.uri.ifBlank { "/" }
        val method = session.method

        if (uri.startsWith("/assets/icons/") && method == Method.GET) {
            val iconName = uri.removePrefix("/assets/icons/").trim('/')
            if (iconName.isBlank() || iconName.contains('/') || iconName.contains("..") || !iconName.endsWith(".svg")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
            val stream = runCatching { context.assets.open("icons/$iconName") }.getOrNull()
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            val size = runCatching { stream.available().toLong() }.getOrDefault(-1L)
            return if (size > 0L) {
                newFixedLengthResponse(Response.Status.OK, "image/svg+xml", stream, size).also {
                    it.addHeader("Cache-Control", "public, max-age=3600")
                }
            } else {
                newChunkedResponse(Response.Status.OK, "image/svg+xml", stream).also {
                    it.addHeader("Cache-Control", "public, max-age=3600")
                }
            }
        }

        if (method == Method.GET && uri == "$webUiAssetBasePath/style.css") {
            val body = webUiStyleCssBytes
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            return serveWebUiAsset(
                session = session,
                mimeType = "text/css; charset=utf-8",
                body = body,
                etag = "\"$webUiAssetToken-css\"",
            )
        }

        if (method == Method.GET && uri == "$webUiAssetBasePath/app.js") {
            val body = webUiAppJsBytes
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            return serveWebUiAsset(
                session = session,
                mimeType = "application/javascript; charset=utf-8",
                body = body,
                etag = "\"$webUiAssetToken-js\"",
            )
        }

        if (method == Method.GET && uri == "$webUiAssetBasePath/favicon.ico") {
            val body = webUiFaviconBytes
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            return serveWebUiAsset(
                session = session,
                mimeType = "image/x-icon",
                body = body,
                etag = "\"$webUiAssetToken-favicon\"",
            )
        }

        if (uri.startsWith("/upload-cancel/") && (method == Method.POST || method == Method.DELETE)) {
            if (config.readOnlyFileserver) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Read-only mode enabled")
            }
            val path = uri.removePrefix("/upload-cancel/").trim('/')
            if (path.isBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing upload path")
            }
            val canceled = uploadHandler.cancelUpload(path)
            markCanceledTransfers(path)
            return if (canceled) {
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Upload canceled")
            } else {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No partial upload found")
            }
        }

        if (uri.startsWith("/mkdir/") && method == Method.POST) {
            if (config.readOnlyFileserver) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Read-only mode enabled")
            }
            val path = uri.removePrefix("/mkdir/").trim('/')
            if (path.isBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing directory path")
            }
            val created = createDirectoryPath(path)
            return if (created) {
                cache.invalidate("")
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Directory created")
            } else {
                newFixedLengthResponse(Response.Status.CONFLICT, MIME_PLAINTEXT, "Directory exists or invalid path")
            }
        }

        if (uri.startsWith("/delete/") && (method == Method.POST || method == Method.DELETE)) {
            if (config.readOnlyFileserver) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Read-only mode enabled")
            }
            val path = uri.removePrefix("/delete/").trim('/')
            if (path.isBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing delete path")
            }
            val doc = directoryHandler.resolve(path)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            val deleted = runCatching { doc.delete() }.getOrDefault(false)
            return if (deleted) {
                cache.invalidate("")
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
            } else {
                newFixedLengthResponse(Response.Status.CONFLICT, MIME_PLAINTEXT, "Delete failed")
            }
        }

        if (uri.startsWith("/upload/") && method == Method.POST) {
            if (config.readOnlyFileserver) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Read-only mode enabled")
            }
            if (!config.allowUploads) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Uploads disabled")
            }
            val uploadStartMs = System.currentTimeMillis()
            val path = uri.removePrefix("/upload/")
            val fileName = path.substringAfterLast('/')
            val key = "$ip|$path"
            val transferId = uploadTransferIds.getOrPut(key) { UUID.randomUUID().toString() }
            val contentRange = parseContentRange(session.headers["content-range"])
            val totalBytes = contentRange?.total ?: (session.headers["content-length"]?.toLongOrNull() ?: 0L)
            val chunkBytes = contentRange?.let { it.end - it.start + 1 }
                ?: (session.headers["content-length"]?.toLongOrNull() ?: 0L)
            val currentTransferred = contentRange?.start ?: 0L
            val existingTransfer = ServerStateStore.transfers.value.orEmpty().firstOrNull { it.id == transferId }
            ServerStateStore.upsertTransfer(
                TransferItem(
                    id = transferId,
                    fileName = fileName,
                    relativePath = path,
                    direction = Direction.UPLOAD,
                    totalBytes = totalBytes,
                    transferredBytes = maxOf(currentTransferred, existingTransfer?.transferredBytes ?: 0L),
                    speedBps = existingTransfer?.speedBps ?: 0L,
                    clientIp = ip,
                    status = TransferStatus.ACTIVE,
                ),
            )

            val result = uploadHandler.handleUpload(path, session)
            cache.invalidate("")
            val durationMs = (System.currentTimeMillis() - uploadStartMs).coerceAtLeast(1L)
            val chunkSpeed = if (chunkBytes > 0L) {
                maxOf(1L, (chunkBytes * 1000L) / durationMs)
            } else {
                0L
            }
            val transferredAfter = contentRange?.let { (it.end + 1).coerceAtMost(totalBytes) } ?: totalBytes
            val status = when (result.status) {
                Response.Status.OK -> TransferStatus.COMPLETED
                Response.Status.ACCEPTED -> TransferStatus.ACTIVE
                Response.Status.GONE -> TransferStatus.CANCELLED
                else -> TransferStatus.FAILED
            }
            ServerStateStore.upsertTransfer(
                TransferItem(
                    id = transferId,
                    fileName = fileName,
                    relativePath = path,
                    direction = Direction.UPLOAD,
                    totalBytes = totalBytes,
                    transferredBytes = transferredAfter,
                    speedBps = chunkSpeed,
                    clientIp = ip,
                    status = status,
                ),
            )
            if (
                status == TransferStatus.COMPLETED ||
                status == TransferStatus.FAILED ||
                status == TransferStatus.CANCELLED
            ) {
                uploadTransferIds.remove(key)
            }
            return result
        }

        if (uri.startsWith("/zip") && method == Method.GET) {
            if (!config.allowZipDownload) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "ZIP download disabled")
            }
            val path = uri.removePrefix("/zip").trim('/')
            return directoryHandler.serveZip(path)
        }

        if (uri.startsWith("/token/") && method == Method.GET) {
            val token = uri.removePrefix("/token/")
            val path = tokenManager.consume(token)
                ?: return newFixedLengthResponse(Response.Status.GONE, MIME_PLAINTEXT, "Token expired")
            val doc = directoryHandler.resolve(path)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            if (!doc.isFile) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Not a file")
            return downloadHandler.serveFile(doc, session)
        }

        if (uri == "/token" && method == Method.POST) {
            val path = session.parameters["path"]?.firstOrNull()?.trim('/')
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing path")
            val token = tokenManager.create(path)
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "$serverBase/token/$token")
        }

        if (method == Method.GET) {
            val path = uri.trim('/').removePrefix("/")
            val doc = directoryHandler.resolve(path)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            return when {
                doc.isDirectory -> directoryHandler.serveDirectory(path, serverBase, webUiAssetBasePath)
                doc.isFile -> serveFileWithTransfer(path, doc, ip, session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        }

        return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
    }

    private fun authenticate(session: IHTTPSession): Boolean {
        if (config.anonymousAccess) return true
        val authHeader = session.headers["authorization"] ?: return false
        if (!authHeader.startsWith("Basic ")) return false
        return runCatching {
            val decoded = String(Base64.decode(authHeader.substring(6), Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split(':', limit = 2)
            parts.size == 2 && parts[0] == config.userId && parts[1] == config.password
        }.getOrDefault(false)
    }

    private fun unauthorizedResponse(): Response {
        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized").also {
            it.addHeader("WWW-Authenticate", "Basic realm=\"WiFi File Share\"")
        }
    }

    private fun hasTraversal(uri: String): Boolean {
        val normalized = uri.replace("\\", "/")
        return normalized.contains("../") || normalized.endsWith("..")
    }

    private fun allowRate(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val queue = requestWindow.getOrPut(ip) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && now - queue.first() > 1000L) {
                queue.removeFirst()
            }
            if (queue.size >= 20) return false
            queue.addLast(now)
            return true
        }
    }

    private fun markCanceledTransfers(path: String) {
        val suffix = "|$path"
        val affected = uploadTransferIds.entries.filter { it.key.endsWith(suffix) }
        for ((key, transferId) in affected) {
            val existing = ServerStateStore.transfers.value.orEmpty().firstOrNull { it.id == transferId }
            if (existing != null) {
                ServerStateStore.upsertTransfer(
                    existing.copy(
                        speedBps = 0L,
                        status = TransferStatus.CANCELLED,
                    ),
                )
            }
            uploadTransferIds.remove(key)
        }
    }

    private fun serveWebUiAsset(
        session: IHTTPSession,
        mimeType: String,
        body: ByteArray,
        etag: String,
    ): Response {
        val ifNoneMatch = session.headers["if-none-match"]?.trim()
        val response = if (ifNoneMatch == etag) {
            newFixedLengthResponse(Response.Status.NOT_MODIFIED, mimeType, "")
        } else {
            newFixedLengthResponse(Response.Status.OK, mimeType, ByteArrayInputStream(body), body.size.toLong())
        }
        response.addHeader("Cache-Control", "public, max-age=600")
        response.addHeader("ETag", etag)
        return response
    }

    private fun readAssetBytes(assetPath: String): ByteArray? {
        return runCatching { context.assets.open(assetPath).use { it.readBytes() } }.getOrNull()
    }

    private fun generateWebUiAssetToken(): String {
        val randomBytes = ByteArray(18)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun serveFileWithTransfer(path: String, doc: DocumentFile, ip: String, session: IHTTPSession): Response {
        val transferId = UUID.randomUUID().toString()
        val size = doc.length().coerceAtLeast(0L)
        val range = session.headers["range"]?.let { parseRangeHeader(it, size) }
        val transferTotal = range?.let { it.second - it.first + 1 } ?: size
        val fileName = doc.name ?: "file"

        ServerStateStore.upsertTransfer(
            TransferItem(
                id = transferId,
                fileName = fileName,
                relativePath = path,
                direction = Direction.DOWNLOAD,
                totalBytes = transferTotal,
                transferredBytes = 0L,
                speedBps = 0L,
                clientIp = ip,
                status = TransferStatus.ACTIVE,
            ),
        )

        val response = downloadHandler.serveFile(doc, session) { transferred, speedBps, done ->
            ServerStateStore.upsertTransfer(
                TransferItem(
                    id = transferId,
                    fileName = fileName,
                    relativePath = path,
                    direction = Direction.DOWNLOAD,
                    totalBytes = transferTotal,
                    transferredBytes = transferred.coerceAtMost(transferTotal),
                    speedBps = speedBps,
                    clientIp = ip,
                    status = if (done) TransferStatus.COMPLETED else TransferStatus.ACTIVE,
                ),
            )
        }

        if (response.status != Response.Status.OK && response.status != Response.Status.PARTIAL_CONTENT) {
            ServerStateStore.upsertTransfer(
                TransferItem(
                    id = transferId,
                    fileName = fileName,
                    relativePath = path,
                    direction = Direction.DOWNLOAD,
                    totalBytes = transferTotal,
                    transferredBytes = 0L,
                    speedBps = 0L,
                    clientIp = ip,
                    status = TransferStatus.FAILED,
                ),
            )
        }

        return response
    }

    private fun createDirectoryPath(path: String): Boolean {
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return false
        if (segments.any { it == "." || it == ".." }) return false

        var current = root
        for ((index, segment) in segments.withIndex()) {
            val existing = current.findFile(segment)
            val isLast = index == segments.lastIndex
            current = when {
                existing == null -> {
                    val created = current.createDirectory(segment) ?: return false
                    if (isLast) return true
                    created
                }
                existing.isDirectory -> {
                    if (isLast) return false
                    existing
                }
                else -> return false
            }
        }
        return false
    }
}
