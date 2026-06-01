package me.ibrahimrafi.wififileserver.server

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import me.ibrahimrafi.wififileserver.model.ServerConfig
import me.ibrahimrafi.wififileserver.storage.DocumentTreeCache
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DirectoryHandler(
    private val context: Context,
    private val cache: DocumentTreeCache,
    private val config: ServerConfig,
) {
    fun resolve(path: String): DocumentFile? = cache.resolvePath(path)

    fun serveDirectory(path: String, internalBase: String): NanoHTTPD.Response {
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
            internalBase = internalBase,
            allowUploads = config.allowUploads && !config.readOnlyFileserver,
            allowCreateFolder = !config.readOnlyFileserver && !config.dropBoxMode,
            allowDelete = !config.readOnlyFileserver && !config.dropBoxMode,
            allowZipDownload = config.allowZipDownload && !config.dropBoxMode,
            dropBoxMode = config.dropBoxMode,
            entries = if (config.dropBoxMode) emptyList() else items,
        )

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", html)
    }

    fun serveZipOfPaths(paths: List<String>): NanoHTTPD.Response {
        val docs = paths
            .map { it.trim('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .mapNotNull { p -> resolve(p) }
            .filter { d -> d.name?.let { isSafeZipEntryName(it) } == true }
        if (docs.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "No matching items")
        }

        val pipedIn = PipedInputStream(64 * 1024)
        val pipedOut = PipedOutputStream(pipedIn)
        Thread({
            runCatching {
                ZipOutputStream(pipedOut.buffered(64 * 1024)).use { zip ->
                    for (doc in docs) {
                        val base = doc.name ?: continue
                        if (doc.isDirectory) {
                            writeDirectoryToZip(zip, doc, base)
                        } else {
                            zip.putNextEntry(ZipEntry(base))
                            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                                input.copyTo(zip, 64 * 1024)
                            }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }, "zip-multi-writer").start()

        val archiveName = "wifiserver-${System.currentTimeMillis()}.zip"
        return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/zip", pipedIn).also {
            it.addHeader("Content-Disposition", "attachment; filename*=UTF-8''${percentEncodeFileName(archiveName)}")
        }
    }

    fun serveZip(path: String): NanoHTTPD.Response {
        val dir = resolve(path)
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found")
        if (!dir.isDirectory) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Not a directory")
        }

        val pipedIn = PipedInputStream(64 * 1024)
        val pipedOut = PipedOutputStream(pipedIn)
        Thread({
            runCatching {
                ZipOutputStream(pipedOut.buffered(64 * 1024)).use { zip ->
                    writeDirectoryToZip(zip, dir, "")
                }
            }
        }, "zip-writer").start()

        return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/zip", pipedIn).also {
            val dirName = percentEncodeFileName(dir.name ?: "directory")
            it.addHeader("Content-Disposition", "attachment; filename*=UTF-8''$dirName.zip")
        }
    }

    private fun writeDirectoryToZip(
        zip: ZipOutputStream,
        dir: DocumentFile,
        basePath: String,
        depth: Int = 0,
    ) {
        if (depth > MAX_ZIP_DEPTH) return
        for (child in dir.listFiles()) {
            val rawName = child.name?.takeIf { it.isNotBlank() } ?: continue
            if (!isSafeZipEntryName(rawName)) continue
            val childPath = if (basePath.isBlank()) rawName else "$basePath/$rawName"
            if (child.isDirectory) {
                writeDirectoryToZip(zip, child, childPath, depth + 1)
            } else {
                zip.putNextEntry(ZipEntry(childPath))
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    input.copyTo(zip, 64 * 1024)
                }
                zip.closeEntry()
            }
        }
    }

    private fun isSafeZipEntryName(name: String): Boolean {
        if (name == "." || name == "..") return false
        return name.none { it == '/' || it == '\\' || it == '\u0000' }
    }

    companion object {
        private const val MAX_ZIP_DEPTH = 64
    }
}

data class DirectoryEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val relativePath: String,
)
