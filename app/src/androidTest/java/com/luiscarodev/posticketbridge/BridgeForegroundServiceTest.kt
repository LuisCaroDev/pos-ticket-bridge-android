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
import java.net.ServerSocket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test
    fun changingPortKeepsPreviousListenerWhenCandidateIsOccupied() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as BridgeApplication
        if (Build.VERSION.SDK_INT >= 37) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            )
        }
        val originalPort = application.settingsRepository.getOrCreate().port
        val availablePort = ServerSocket(0).use { it.localPort }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                BridgeForegroundService.start(context)
                waitUntil { application.runtimeRepository.state.value is BridgeRuntimeState.Running }
                withTimeout(15_000) { application.runtimeRepository.changePort(availablePort) }
                assertEquals(availablePort, application.settingsRepository.getOrCreate().port)
                assertBridgeHealthSucceeds(application)

                BridgeForegroundService.stop(context)
                waitUntil { application.runtimeRepository.state.value == BridgeRuntimeState.Stopped }
                BridgeForegroundService.start(context)
                waitUntil {
                    (application.runtimeRepository.state.value as? BridgeRuntimeState.Running)?.port == availablePort
                }
                assertBridgeHealthSucceeds(application)

                ServerSocket(0).use { occupied ->
                    val failure = runCatching {
                        withTimeout(15_000) {
                            application.runtimeRepository.changePort(occupied.localPort)
                        }
                    }.exceptionOrNull()
                    assertNotNull(failure)
                    assertEquals(availablePort, application.settingsRepository.getOrCreate().port)
                    assertBridgeHealthSucceeds(application)
                }
            }
        } finally {
            if (application.settingsRepository.getOrCreate().port != originalPort) {
                runCatching {
                    withTimeout(15_000) { application.runtimeRepository.changePort(originalPort) }
                }
            }
            BridgeForegroundService.stop(context)
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val port = runBlocking {
            (context.applicationContext as BridgeApplication).settingsRepository.getOrCreate().port
        }
        val connection = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000

        assertEquals(200, connection.responseCode)
        assertTrue(connection.inputStream.bufferedReader().use { it.readText() }.contains("\"ok\":true"))
        connection.disconnect()
    }

    private fun assertBridgeHealthSucceeds(application: BridgeApplication) {
        val status = application.httpsRepository.state.value
        val host = if (status.transport == "https") status.host else {
            val port = (application.runtimeRepository.state.value as BridgeRuntimeState.Running).port
            "http://127.0.0.1:$port"
        }
        val connection = URL("$host/health").openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = com.luiscarodev.posticketbridge.bridge.https.HttpsCertificates
                .clientContext(requireNotNull(application.httpsRepository.clientCa)).socketFactory
        }
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        assertEquals(200, connection.responseCode)
        connection.disconnect()
    }
}
