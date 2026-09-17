package com.luiscarodev.posticketbridge

import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.luiscarodev.posticketbridge.bridge.BridgeRuntimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class NotificationStopTest {
    @Test fun notificationStopsServiceAndResumeDoesNotRestartIt(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as BridgeApplication
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            withTimeout(15_000) { app.runtimeRepository.state.first { it is BridgeRuntimeState.Running } }
            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = manager.activeNotifications.single { it.id == 9977 }.notification
            val stop = notification.actions.single { it.title.toString() == "Detener" }
            stop.actionIntent.send()
            withTimeout(15_000) { app.runtimeRepository.state.first { it == BridgeRuntimeState.Stopped } }
            assertEquals("stopped", app.httpsRepository.state.value.transport)
            assertNull(app.httpsRepository.state.value.enrollment)
            assertFalse(manager.activeNotifications.any { it.id == 9977 })
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            delay(1_000)
            assertEquals(BridgeRuntimeState.Stopped, app.runtimeRepository.state.value)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            withTimeout(15_000) { app.runtimeRepository.state.first { it is BridgeRuntimeState.Running } }
        }
    }
}
