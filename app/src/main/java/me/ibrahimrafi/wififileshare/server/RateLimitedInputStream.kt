package me.ibrahimrafi.wififileshare.server

import java.io.FilterInputStream
import java.io.InputStream

class RateLimitedInputStream(
    input: InputStream,
    private val bytesPerSecond: Long,
) : FilterInputStream(input) {
    private var windowStart = System.nanoTime()
    private var bytesInWindow = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) {
            throttle(1)
        }
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            throttle(count)
        }
        return count
    }

    private fun throttle(readCount: Int) {
        if (bytesPerSecond <= 0L || bytesPerSecond == Long.MAX_VALUE) return
        bytesInWindow += readCount
        val elapsedNs = System.nanoTime() - windowStart
        val expectedNs = (bytesInWindow * 1_000_000_000L) / bytesPerSecond
        if (expectedNs > elapsedNs) {
            val sleepMs = (expectedNs - elapsedNs) / 1_000_000L
            if (sleepMs > 0L) {
                Thread.sleep(sleepMs)
            }
        }
        if (elapsedNs > 1_000_000_000L) {
            windowStart = System.nanoTime()
            bytesInWindow = 0L
        }
    }
}
