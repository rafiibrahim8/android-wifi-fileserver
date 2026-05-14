package me.ibrahimrafi.wififileserver.server

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
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

private object InsufficientStorageStatus : NanoHTTPD.Response.IStatus {
    override fun getRequestStatus(): Int = 507
    override fun getDescription(): String = "507 Insufficient Storage"
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

    private fun partNameFor(fileName: String): String = "$fileName$PART_SUFFIX"

    fun cancelUpload(relativePath: String): Boolean {
        val cleanedPath = normalizePath(relativePath)
        if (cleanedPath.isEmpty()) return false
        val key = cleanedPath.joinToString("/")
        canceledUploads.add(key)

        val fileName = cleanedPath.last()
        val parent = resolveExistingDirectories(cleanedPath.dropLast(1))

        partialUploads.remove(key)?.let {
            runCatching { context.contentResolver.delete(it.uri, null, null) > 0 }
        }

        parent?.findFile(partNameFor(fileName))?.let {
            it.delete()
        }

        return true
    }

    fun handleUpload(relativePath: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val cleanedPath = normalizePath(relativePath)
        if (cleanedPath.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing file")
        }
        val key = cleanedPath.joinToString("/")
        if (canceledUploads.contains(key)) {
            cleanupPartial(key, cleanedPath)
            return canceledResponse()
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

        // Cheap free-space check: reject obviously impossible uploads before opening any stream.
        // Conservative — relies on primary external storage as a proxy; some SAF roots live
        // elsewhere, in which case the check is skipped via best-effort fallback.
        val freeBytes = availableStorageBytes()
        val remainingNeeded = totalUploadSize - (contentRange?.start ?: 0L)
        if (freeBytes > 0L && remainingNeeded > 0L && remainingNeeded > freeBytes) {
            return NanoHTTPD.newFixedLengthResponse(
                InsufficientStorageStatus,
                NanoHTTPD.MIME_PLAINTEXT,
                "Not enough free space",
            )
        }

        val partName = partNameFor(fileName)

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
                return canceledResponse()
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
        resolveExistingDirectories(cleanedPath.dropLast(1))?.findFile(partNameFor(fileName))?.delete()
        canceledUploads.remove(key)
    }

    private fun normalizePath(relativePath: String): List<String> {
        return relativePath.trim('/').split('/').filter { it.isNotBlank() }
    }

    /**
     * 410 response for a cancelled upload. Asks the connection to close so the PC's TCP
     * stack drops the in-flight chunk instead of finishing it before noticing the 410.
     */
    private fun canceledResponse(): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.GONE,
            NanoHTTPD.MIME_PLAINTEXT,
            "Upload canceled by server",
        ).also { it.addHeader("Connection", "close") }
    }

    /**
     * Best-effort free-space query for the volume backing the SAF root. Returns -1 when
     * unknown so callers don't gate on the result.
     *
     * On API 30+ we resolve the actual [StorageVolume] for the root URI; older builds fall
     * back to primary external storage, which is right in the common case and harmless
     * (over-cautious) when the user picked an SD card.
     */
    private fun availableStorageBytes(): Long {
        return runCatching {
            val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                sm?.getStorageVolume(root.uri)?.directory?.path
                    ?: Environment.getExternalStorageDirectory()?.path
            } else {
                Environment.getExternalStorageDirectory()?.path
            } ?: return@runCatching -1L
            StatFs(path).availableBytes
        }.getOrDefault(-1L)
    }

    /**
     * Recursively delete partial-upload sidecar files older than [maxAgeMs]. Only matches
     * files with our own [PART_SUFFIX] so files the user happens to name `*.part` are left
     * alone. Intended to be called once per server start. Returns the number of files removed.
     */
    fun sweepStalePartials(maxAgeMs: Long = 24L * 60L * 60L * 1000L): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        var removed = 0
        sweep(root, cutoff, depth = 0) { removed++ }
        return removed
    }

    private fun sweep(dir: DocumentFile, cutoff: Long, depth: Int, onRemove: () -> Unit) {
        if (depth > MAX_SWEEP_DEPTH) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isDirectory) {
                sweep(child, cutoff, depth + 1, onRemove)
                continue
            }
            val name = child.name ?: continue
            if (!name.endsWith(PART_SUFFIX)) continue
            val modified = runCatching { child.lastModified() }.getOrDefault(0L)
            if (modified in 1 until cutoff) {
                if (runCatching { child.delete() }.getOrDefault(false)) onRemove()
            }
        }
    }

    companion object {
        private const val MAX_SWEEP_DEPTH = 32

        // Distinctive sidecar suffix so the sweep never touches user files that happen to
        // end in `.part`. Visible to clients during resumable uploads, then renamed away.
        private const val PART_SUFFIX = ".wifiserver.part"
    }
}
