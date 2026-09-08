package com.luiscarodev.posticketbridge

import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luiscarodev.posticketbridge.bridge.BridgeForegroundService
import com.luiscarodev.posticketbridge.bridge.BridgeRuntimeState
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BridgeForegroundServiceTest {
    @Test
    fun activityRecreationKeepsOneForegroundBridgeRunning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = context.applicationContext as BridgeApplication
        runBlocking { application.settingsRepository.getOrCreate() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        if (Build.VERSION.SDK_INT >= 37) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            )
        }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                BridgeForegroundService.start(context)
                scenario.recreate()
                waitUntil { application.runtimeRepository.state.value is BridgeRuntimeState.Running }
            }

            assertTrue(application.runtimeRepository.state.value is BridgeRuntimeState.Running)
            assertBridgeHealthSucceeds()
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            assertNotNull(notificationManager.getNotificationChannel("bridge_runtime"))
        } finally {
            context.stopService(Intent(context, BridgeForegroundService::class.java))
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(50) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("Bridge did not reach Running state", condition())
    }

    private fun assertBridgeHealthSucceeds() {
        val connection = URL("http://127.0.0.1:9977/health").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000

        assertEquals(200, connection.responseCode)
        assertTrue(connection.inputStream.bufferedReader().use { it.readText() }.contains("\"ok\":true"))
        connection.disconnect()
    }
}
