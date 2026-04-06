package me.ibrahimrafi.wififileshare.server

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import me.ibrahimrafi.wififileshare.model.ServerConfig
import me.ibrahimrafi.wififileshare.storage.DocumentTreeCache
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DirectoryHandler(
    private val context: Context,
    private val cache: DocumentTreeCache,
    private val config: ServerConfig,
    private val tokenManager: AccessTokenManager,
) {
    fun resolve(path: String): DocumentFile? = cache.resolvePath(path)

    fun serveDirectory(path: String, serverBase: String, assetBasePath: String): NanoHTTPD.Response {
        val dir = resolve(path)
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found")
        if (!dir.isDirectory) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Not a directory")
        }

        val items = cache.listChildren(path)
            .asSequence()
            .filter { config.showHiddenFiles || !(it.name ?: "").startsWith('.') }
            .map { doc ->
                val relativePath = if (path.isBlank()) {
                    doc.name ?: ""
                } else {
                    path.trim('/') + "/" + (doc.name ?: "")
                }
                DirectoryEntry(
                    name = doc.name ?: "unnamed",
                    isDirectory = doc.isDirectory,
                    size = if (doc.isFile) doc.length() else -1L,
                    modified = doc.lastModified(),
                    relativePath = relativePath,
                )
            }
            .sortedWith(compareByDescending<DirectoryEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()

        val html = HtmlPageBuilder.build(
            context = context,
            currentPath = path,
            serverBase = serverBase,
            assetBasePath = assetBasePath,
            allowUploads = config.allowUploads && !config.readOnlyFileserver,
            allowCreateFolder = !config.readOnlyFileserver,
            allowDelete = !config.readOnlyFileserver,
            allowZipDownload = config.allowZipDownload,
            anonymousAccess = config.anonymousAccess,
            entries = items,
        )

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", html)
    }

    fun serveZip(path: String): NanoHTTPD.Response {
        val dir = resolve(path)
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found")
        if (!dir.isDirectory) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Not a directory")
        }

        val pipedIn = PipedInputStream(64 * 1024)
        val pipedOut = PipedOutputStream(pipedIn)
        Thread {
            ZipOutputStream(pipedOut.buffered(64 * 1024)).use { zip ->
                writeDirectoryToZip(zip, dir, "")
            }
        }.start()

        return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/zip", pipedIn).also {
            val dirName = percentEncodeFileName(dir.name ?: "directory")
            it.addHeader("Content-Disposition", "attachment; filename*=UTF-8''$dirName.zip")
        }
    }

    fun createToken(path: String): String {
        return tokenManager.create(path)
    }

    private fun writeDirectoryToZip(zip: ZipOutputStream, dir: DocumentFile, basePath: String) {
        for (child in dir.listFiles()) {
            val childPath = if (basePath.isBlank()) (child.name ?: "") else "$basePath/${child.name}"
            if (child.isDirectory) {
                writeDirectoryToZip(zip, child, childPath)
            } else {
                val entryName = childPath.ifBlank { child.name ?: "file" }
                zip.putNextEntry(ZipEntry(entryName))
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    input.copyTo(zip, 64 * 1024)
                }
                zip.closeEntry()
            }
        }
    }
}

data class DirectoryEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val relativePath: String,
)
