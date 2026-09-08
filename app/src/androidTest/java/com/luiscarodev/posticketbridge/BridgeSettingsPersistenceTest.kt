package com.luiscarodev.posticketbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luiscarodev.posticketbridge.data.BridgeSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BridgeSettingsPersistenceTest {
    @Test
    fun tokenAndMultipleOriginsSurviveRepositoryRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstRepository = BridgeSettingsRepository(context)
        val first = firstRepository.getOrCreate()
        firstRepository.saveAllowedOrigins(
            listOf(
                "https://pos.example.com",
                "http://localhost:5173",
                "https://pos.example.com",
            ),
        )

        val restored = BridgeSettingsRepository(context).getOrCreate()
        assertTrue(first.token.isNotEmpty())
        assertEquals(first.token, restored.token)
        assertEquals(
            listOf("https://pos.example.com", "http://localhost:5173"),
            restored.allowedOrigins,
        )
    }
}
