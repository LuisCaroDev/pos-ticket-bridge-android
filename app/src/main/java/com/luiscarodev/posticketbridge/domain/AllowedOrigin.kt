package com.luiscarodev.posticketbridge.domain

import java.net.URI
import java.util.Locale

object AllowedOrigin {
    fun normalizeOrNull(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.rawPath.orEmpty() !in setOf("", "/")) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.port !in -1..65535 || uri.port == 0) return null

        val normalizedPort = when {
            scheme == "http" && uri.port == 80 -> -1
            scheme == "https" && uri.port == 443 -> -1
            else -> uri.port
        }
        return URI(
            scheme,
            null,
            uri.host.lowercase(Locale.ROOT),
            normalizedPort,
            null,
            null,
            null,
        ).toASCIIString()
    }
}
