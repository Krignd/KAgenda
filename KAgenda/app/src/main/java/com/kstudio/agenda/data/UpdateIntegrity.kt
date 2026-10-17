package com.kstudio.agenda.data

import java.io.File
import java.net.URI
import java.security.MessageDigest

internal object UpdateIntegrity {
    const val MAX_APK_BYTES = 256L * 1024 * 1024

    fun requireHttps(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Invalid HTTPS URL" }
    }

    fun matches(file: File, size: Long, sha256: String): Boolean {
        if (!file.isFile || file.length() !in 1..MAX_APK_BYTES || (size > 0 && file.length() != size)) return false
        if (sha256.isBlank()) return true
        if (!sha256.matches(Regex("[a-fA-F0-9]{64}"))) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(sha256, ignoreCase = true)
    }
}
