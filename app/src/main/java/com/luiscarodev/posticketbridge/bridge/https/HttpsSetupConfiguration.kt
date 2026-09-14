package com.luiscarodev.posticketbridge.bridge.https

import com.luiscarodev.posticketbridge.BuildConfig
import java.net.URI

class HttpsSetupConfiguration(private val values: Map<String, String> = emptyMap()) {
    val durationMs: Long = values["POS_BRIDGE_HTTPS_SETUP_TTL_MS"]?.trim()?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }
        ?.toLong() ?: ENROLLMENT_DURATION_MS

    fun video(os: ClientOs): String? = validUrl(values["POS_BRIDGE_HTTPS_VIDEO_${os.name}"])
    fun guide(os: ClientOs): String = validUrl(values["POS_BRIDGE_HTTPS_GUIDE_${os.name}"]) ?: os.guide

    private fun validUrl(value: String?): String? = value?.trim()?.takeIf { url ->
        runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        }.getOrDefault(false)
    }

    companion object {
        val bundled = HttpsSetupConfiguration(mapOf(
            "POS_BRIDGE_HTTPS_SETUP_TTL_MS" to BuildConfig.POS_BRIDGE_HTTPS_SETUP_TTL_MS,
            "POS_BRIDGE_HTTPS_VIDEO_IOS" to BuildConfig.POS_BRIDGE_HTTPS_VIDEO_IOS,
            "POS_BRIDGE_HTTPS_VIDEO_ANDROID" to BuildConfig.POS_BRIDGE_HTTPS_VIDEO_ANDROID,
            "POS_BRIDGE_HTTPS_VIDEO_WINDOWS" to BuildConfig.POS_BRIDGE_HTTPS_VIDEO_WINDOWS,
            "POS_BRIDGE_HTTPS_VIDEO_MACOS" to BuildConfig.POS_BRIDGE_HTTPS_VIDEO_MACOS,
            "POS_BRIDGE_HTTPS_GUIDE_IOS" to BuildConfig.POS_BRIDGE_HTTPS_GUIDE_IOS,
            "POS_BRIDGE_HTTPS_GUIDE_ANDROID" to BuildConfig.POS_BRIDGE_HTTPS_GUIDE_ANDROID,
            "POS_BRIDGE_HTTPS_GUIDE_WINDOWS" to BuildConfig.POS_BRIDGE_HTTPS_GUIDE_WINDOWS,
            "POS_BRIDGE_HTTPS_GUIDE_MACOS" to BuildConfig.POS_BRIDGE_HTTPS_GUIDE_MACOS,
        ))
    }
}
