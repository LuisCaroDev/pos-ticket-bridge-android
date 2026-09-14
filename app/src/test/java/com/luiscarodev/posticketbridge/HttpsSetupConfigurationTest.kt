package com.luiscarodev.posticketbridge

import com.luiscarodev.posticketbridge.bridge.https.*
import org.junit.Assert.*
import org.junit.Test

class HttpsSetupConfigurationTest {
    @Test fun defaultsAndPerPlatformOverrides() {
        val defaults = HttpsSetupConfiguration()
        assertEquals(600_000L, defaults.durationMs)
        ClientOs.entries.forEach { os ->
            assertNull(defaults.video(os))
            assertEquals(os.guide, defaults.guide(os))
            val custom = HttpsSetupConfiguration(mapOf(
                "POS_BRIDGE_HTTPS_VIDEO_${os.name}" to " https://example.com/video ",
                "POS_BRIDGE_HTTPS_GUIDE_${os.name}" to "https://example.com/guide"))
            assertEquals("https://example.com/video", custom.video(os))
            assertEquals("https://example.com/guide", custom.guide(os))
            ClientOs.entries.filter { it != os }.forEach { assertNull(custom.video(it)) }
        }
    }

    @Test fun invalidLinksAreHiddenOrFallBack() {
        listOf("", "http://example.com", "https://user:password@example.com", "javascript:alert(1)",
            "https:///missing-host", "https://example.com/bad path").forEach { url ->
            val setup = HttpsSetupConfiguration(mapOf("POS_BRIDGE_HTTPS_VIDEO_WINDOWS" to url,
                "POS_BRIDGE_HTTPS_GUIDE_WINDOWS" to url))
            assertNull(setup.video(ClientOs.WINDOWS))
            assertEquals(ClientOs.WINDOWS.guide, setup.guide(ClientOs.WINDOWS))
        }
    }

    @Test fun durationUsesDesktopBounds() {
        listOf("", "0", "-1", "1.5", "NaN", "Infinity", "2147483648", "invalid").forEach {
            assertEquals(600_000L, HttpsSetupConfiguration(mapOf("POS_BRIDGE_HTTPS_SETUP_TTL_MS" to it)).durationMs)
        }
        listOf("1" to 1L, "90000" to 90000L, "2147483647" to 2147483647L, "6e5" to 600000L).forEach { (raw, expected) ->
            assertEquals(expected, HttpsSetupConfiguration(mapOf("POS_BRIDGE_HTTPS_SETUP_TTL_MS" to raw)).durationMs)
        }
    }
}
