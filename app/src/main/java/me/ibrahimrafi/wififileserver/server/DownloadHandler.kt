package me.ibrahimrafi.wififileserver.server

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class DownloadHandler(
    private val context: Context,
    private val maxSpeedBps: Long,
) {
    fun serveFile(
        doc: DocumentFile,
        session: NanoHTTPD.IHTTPSession,
        onProgress: ((transferred: Long, speedBps: Long, done: Boolean) -> Unit)? = null,
    ): NanoHTTPD.Response {
        val size = doc.length().coerceAtLeast(0L)
        val mimeType = resolveDownloadMimeType(doc.name, doc.type)
        val rangeHeader = session.headers["range"]

        val response = if (rangeHeader != null && size > 0L) {
            val range = parseRangeHeader(rangeHeader, size)
                ?: return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Invalid range",
            )
            val (start, end) = range
            val length = end - start + 1
            val stream = openStream(doc, start, length, onProgress)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.PARTIAL_CONTENT,
                mimeType,
                stream,
                length,
            ).also {
                it.addHeader("Content-Range", "bytes $start-$end/$size")
                it.addHeader("Content-Length", length.toString())
            }
        } else {
            val stream = openStream(doc, 0L, size, onProgress)
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, stream, size).also {
                it.addHeader("Content-Length", size.toString())
            }
        }

        val safeName = percentEncodeFileName(doc.name ?: "download.bin")
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("Content-Disposition", "attachment; filename*=UTF-8''$safeName")
        return response
    }

    @Throws(IOException::class)
    private fun openStream(
        doc: DocumentFile,
        skip: Long,
        length: Long,
        onProgress: ((transferred: Long, speedBps: Long, done: Boolean) -> Unit)?,
    ): InputStream {
        val raw = context.contentResolver.openInputStream(doc.uri)
            ?: throw IOException("Unable to open stream")
        try {
            raw.skipFully(skip)
            val bounded: InputStream = BoundedInputStream(raw, length)
            val rateLimited = if (maxSpeedBps > 0L && maxSpeedBps != Long.MAX_VALUE) {
                RateLimitedInputStream(bounded, maxSpeedBps)
            } else {
                bounded
            }
            return if (onProgress != null) ProgressInputStream(rateLimited, length, onProgress) else rateLimited
        } catch (t: Throwable) {
            runCatching { raw.close() }
            throw t
        }
    }
}

class BoundedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
    override fun read(): Int {
        if (remaining <= 0L) return -1
        val out = super.read()
        if (out >= 0) remaining--
        return out
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0L) return -1
        val allowed = minOf(len.toLong(), remaining).toInt()
        val count = super.read(b, off, allowed)
        if (count > 0) remaining -= count.toLong()
        return count
    }
}

private class ProgressInputStream(
    input: InputStream,
    private val totalBytes: Long,
    private val onProgress: (transferred: Long, speedBps: Long, done: Boolean) -> Unit,
) : FilterInputStream(input) {
    private var transferred = 0L
    private var startedAtMs = System.currentTimeMillis()
    private var tickStartMs = startedAtMs
    private var tickBytes = 0L
    private var finished = false

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) {
            onReadCount(1)
        } else {
            finishIfNeeded()
        }
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            onReadCount(count)
        } else if (count == -1) {
            finishIfNeeded()
        }
        return count
    }

    override fun close() {
        finishIfNeeded()
        super.close()
    }

    private fun onReadCount(count: Int) {
        transferred += count.toLong()
        tickBytes += count.toLong()

        val now = System.currentTimeMillis()
        val tickElapsed = (now - tickStartMs).coerceAtLeast(1L)
        if (tickElapsed >= 250L) {
            val speed = (tickBytes * 1000L) / tickElapsed
            onProgress(transferred.coerceAtMost(totalBytes), speed, false)
            tickStartMs = now
            tickBytes = 0L
        }
    }

    private fun finishIfNeeded() {
        if (finished) return
        finished = true
        val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
        val avgSpeed = (transferred * 1000L) / elapsed
        onProgress(transferred.coerceAtMost(totalBytes), avgSpeed, true)
    }
}
