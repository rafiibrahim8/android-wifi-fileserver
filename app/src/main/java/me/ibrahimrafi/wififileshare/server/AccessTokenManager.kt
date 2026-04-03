package me.ibrahimrafi.wififileshare.server

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

data class TokenEntry(
    val path: String,
    val expiresAtMs: Long,
    var consumed: Boolean = false,
)

class AccessTokenManager {
    private val map = ConcurrentHashMap<String, TokenEntry>()
    private val random = SecureRandom()

    fun create(path: String): String {
        val token = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        map[token] = TokenEntry(path = path, expiresAtMs = System.currentTimeMillis() + 10 * 60 * 1000L)
        return token
    }

    fun consume(token: String): String? {
        val now = System.currentTimeMillis()
        val entry = map[token] ?: return null
        if (entry.consumed || now > entry.expiresAtMs) {
            map.remove(token)
            return null
        }
        entry.consumed = true
        map.remove(token)
        return entry.path
    }
}
