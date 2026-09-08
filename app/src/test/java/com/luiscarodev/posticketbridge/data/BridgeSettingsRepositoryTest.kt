package com.luiscarodev.posticketbridge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeSettingsRepositoryTest {
    @Test
    fun tokenIsRandomAndDesktopCompatibleLength() {
        val first = BridgeSettingsRepository.generateToken()
        val second = BridgeSettingsRepository.generateToken()

        assertTrue(first.matches(Regex("[0-9a-f]{48}")))
        assertTrue(second.matches(Regex("[0-9a-f]{48}")))
        assertTrue(first != second)
    }

    @Test
    fun originsAreTrimmedDeduplicatedAndKeepOrder() {
        assertEquals(
            listOf("https://pos.example.com", "http://localhost:5173"),
            BridgeSettingsRepository.normalizeOrigins(
                listOf(
                    " https://pos.example.com ",
                    "",
                    "https://pos.example.com",
                    "http://localhost:5173",
                ),
            ),
        )
    }
}
