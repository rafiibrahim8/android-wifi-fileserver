package me.ibrahimrafi.wififileshare.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

private object PayloadTooLargeStatus : NanoHTTPD.Response.IStatus {
    override fun getRequestStatus(): Int = 413
    override fun getDescription(): String = "413 Payload Too Large"
}

private data class PartialUpload(
    val uri: Uri,
    val receivedBytes: Long,
    val totalBytes: Long,
)

private class UploadCanceledException : RuntimeException("Upload canceled")

class UploadHandler(
    private val context: Context,
    private val root: DocumentFile,
    private val uploadSizeLimitBytes: Long,
) {
    private val partialUploads = ConcurrentHashMap<String, PartialUpload>()
    private val canceledUploads = ConcurrentHashMap.newKeySet<String>()

    fun cancelUpload(relativePath: String): Boolean {
        val cleanedPath = normalizePath(relativePath)
        if (cleanedPath.isEmpty()) return false
        val key = cleanedPath.joinToString("/")
        canceledUploads.add(key)

        val fileName = cleanedPath.last()
        val parent = resolveExistingDirectories(cleanedPath.dropLast(1))
        var deletedAny = false

        partialUploads.remove(key)?.let {
            deletedAny = runCatching { context.contentResolver.delete(it.uri, null, null) > 0 }.getOrDefault(false) || deletedAny
        }
        parent?.findFile("$fileName.part")?.let {
            deletedAny = it.delete() || deletedAny
        }

        return deletedAny || true
    }

    fun handleUpload(relativePath: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val cleanedPath = normalizePath(relativePath)
        if (cleanedPath.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing file")
        }
        val key = cleanedPath.joinToString("/")
        if (canceledUploads.contains(key)) {
            cleanupPartial(key, cleanedPath)
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.GONE,
                NanoHTTPD.MIME_PLAINTEXT,
                "Upload canceled",
            )
        }

        val fileName = cleanedPath.last()
        val parent = ensureDirectories(root, cleanedPath.dropLast(1))
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Directory not found")

        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: -1L
        val contentRange = parseContentRange(session.headers["content-range"])
        val expectedBytes = contentRange?.let { it.end - it.start + 1 } ?: contentLength
        if (expectedBytes <= 0L) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.LENGTH_REQUIRED,
                NanoHTTPD.MIME_PLAINTEXT,
                "Missing or invalid content length",
            )
        }

        val totalUploadSize = contentRange?.total ?: contentLength
        if (totalUploadSize > uploadSizeLimitBytes) {
            return NanoHTTPD.newFixedLengthResponse(
                PayloadTooLargeStatus,
                NanoHTTPD.MIME_PLAINTEXT,
                "Upload too large",
            )
        }

        val partName = "$fileName.part"

        val targetDoc = if (contentRange == null) {
            parent.findFile(fileName)?.also { it.delete() }
            parent.createFile("application/octet-stream", fileName)
        } else {
            val existing = partialUploads[key]
            if (existing == null && contentRange.start != 0L) {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Expected chunk start at 0",
                )
            }
            if (existing != null && contentRange.start != existing.receivedBytes) {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.CONFLICT,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Chunk offset mismatch",
                )
            }
            parent.findFile(partName) ?: parent.createFile("application/octet-stream", partName)
        } ?: return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            NanoHTTPD.MIME_PLAINTEXT,
            "Cannot create file",
        )

        val mode = if (contentRange == null || contentRange.start == 0L) "w" else "wa"
        if (contentRange != null) {
            partialUploads[key] = PartialUpload(
                uri = targetDoc.uri,
                receivedBytes = contentRange.start,
                totalBytes = contentRange.total,
            )
        }

        val writeResult = runCatching {
            context.contentResolver.openOutputStream(targetDoc.uri, mode)?.use { out ->
                copyExactly(
                    input = session.inputStream,
                    output = out,
                    bytes = expectedBytes,
                    shouldCancel = { canceledUploads.contains(key) },
                )
            } ?: throw IllegalStateException("Cannot open output stream")
        }
        if (writeResult.isFailure) {
            if (writeResult.exceptionOrNull() is UploadCanceledException) {
                cleanupPartial(key, cleanedPath)
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.GONE,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Upload canceled",
                )
            }
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                NanoHTTPD.MIME_PLAINTEXT,
                "Malformed upload payload",
            )
        }

        if (contentRange == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "Upload complete")
        }

        val received = contentRange.end + 1
        partialUploads[key] = PartialUpload(targetDoc.uri, received, contentRange.total)
        if (received >= contentRange.total) {
            parent.findFile(fileName)
                ?.takeIf { it.uri != targetDoc.uri }
                ?.delete()
            val renamed = targetDoc.renameTo(fileName)
            partialUploads.remove(key)
            canceledUploads.remove(key)
            return if (renamed) {
                NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "Upload complete")
            } else {
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Could not replace existing file",
                )
            }
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.ACCEPTED, NanoHTTPD.MIME_PLAINTEXT, "Chunk accepted")
    }

    private fun copyExactly(
        input: InputStream,
        output: OutputStream,
        bytes: Long,
        shouldCancel: () -> Boolean,
    ) {
        val buffer = ByteArray(64 * 1024)
        var remaining = bytes
        while (remaining > 0L) {
            if (shouldCancel()) {
                throw UploadCanceledException()
            }
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) {
                throw EOFException("Unexpected end of upload stream")
            }
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun ensureDirectories(root: DocumentFile, path: List<String>): DocumentFile? {
        var current = root
        for (segment in path) {
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment) ?: return null
                existing.isDirectory -> existing
                else -> return null
            }
        }
        return current
    }

    private fun resolveExistingDirectories(path: List<String>): DocumentFile? {
        var current = root
        for (segment in path) {
            val existing = current.findFile(segment) ?: return null
            if (!existing.isDirectory) return null
            current = existing
        }
        return current
    }

    private fun cleanupPartial(key: String, cleanedPath: List<String>) {
        partialUploads.remove(key)?.let {
            runCatching { context.contentResolver.delete(it.uri, null, null) }
        }
        val fileName = cleanedPath.lastOrNull() ?: return
        resolveExistingDirectories(cleanedPath.dropLast(1))?.findFile("$fileName.part")?.delete()
        canceledUploads.remove(key)
    }

    private fun normalizePath(relativePath: String): List<String> {
        return relativePath.trim('/').split('/').filter { it.isNotBlank() }
    }
}
