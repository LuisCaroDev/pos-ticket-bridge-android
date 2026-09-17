package com.luiscarodev.posticketbridge.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import com.luiscarodev.posticketbridge.BridgeApplication
import com.luiscarodev.posticketbridge.MainActivity
import com.luiscarodev.posticketbridge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.luiscarodev.posticketbridge.bridge.https.AndroidHttpsStore
import com.luiscarodev.posticketbridge.bridge.https.LocalHttpsController
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BridgeForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transitionMutex = Mutex()
    private val serverLock = Any()
    private var transitionJob: Job? = null
    private var controller: LocalHttpsController? = null
    @Volatile private var destroyed = false

    private val app: BridgeApplication
        get() = application as BridgeApplication

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground(notification(getString(R.string.bridge_starting)))
        scope.launch {
            for (command in app.httpsRepository.commands) {
                try {
                    transitionMutex.withLock {
                        if (controller == null) startServer()
                        synchronized(serverLock) {
                            check(!destroyed) { "bridge_not_running" }
                            requireNotNull(controller).execute(command.action)
                        }
                        command.result.complete(Unit)
                        publishRuntime()
                    }
                } catch (error: Exception) {
                    command.result.completeExceptionally(error)
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    publishRuntime()
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(5_000)
                transitionMutex.withLock {
                    synchronized(serverLock) { runCatching { controller?.reconcile() } }
                    publishRuntime()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            destroyed = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (destroyed) return START_NOT_STICKY
        val restart = intent?.action == ACTION_RESTART
        if (restart || controller == null) {
            transitionJob = scope.launch {
                transitionMutex.withLock {
                    if (restart) stopServer()
                    if (controller == null) startServer()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        transitionJob?.cancel()
        stopServer()
        scope.cancel()
        while (true) {
            val command = app.httpsRepository.commands.tryReceive().getOrNull() ?: break
            command.result.completeExceptionally(IllegalStateException("bridge_not_running"))
        }
        app.runtimeRepository.update(BridgeRuntimeState.Stopped)
        super.onDestroy()
    }

    private suspend fun startServer() {
        app.runtimeRepository.update(BridgeRuntimeState.Starting)
        updateNotification(getString(R.string.bridge_starting))
        runCatching {
            val settings = app.settingsRepository.getOrCreate()
            val nextController = LocalHttpsController(
                AndroidHttpsStore(this), app.httpsRepository,
                { https -> KtorBridgeHttpServer(settings, app.printerRepository, app.printCoordinator, https) },
                settings.port,
            )
            synchronized(serverLock) {
                if (destroyed) return
                controller = nextController
                nextController.restart()
            }
            publishRuntime()
        }.onFailure { error ->
            android.util.Log.e("BridgeRuntime", "Server startup failed", error)
            val reason = error.message ?: error::class.java.simpleName
            app.runtimeRepository.update(BridgeRuntimeState.Failed(reason))
            updateNotification(getString(R.string.bridge_failed))
        }
    }

    private fun stopServer() {
        synchronized(serverLock) {
            runCatching { controller?.shutdown() }
            controller = null
        }
    }

    private fun publishRuntime() {
        if (destroyed) return
        val status = app.httpsRepository.state.value
        if (status.transport == "stopped") {
            val previous = app.runtimeRepository.state.value as? BridgeRuntimeState.Failed
            app.runtimeRepository.update(BridgeRuntimeState.Failed(status.error ?: previous?.reason ?: "bridge_not_running"))
            updateNotification(getString(R.string.bridge_failed))
        } else {
            app.runtimeRepository.update(BridgeRuntimeState.Running(listOf(status.host), com.luiscarodev.posticketbridge.data.BRIDGE_PORT))
            updateNotification(getString(R.string.bridge_running, com.luiscarodev.posticketbridge.data.BRIDGE_PORT))
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        if (destroyed) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BridgeForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.bridge_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.bridge_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "bridge_runtime"
        private const val NOTIFICATION_ID = 9977
        private const val ACTION_RESTART =
            "com.luiscarodev.posticketbridge.action.RESTART_BRIDGE"
        private const val ACTION_STOP =
            "com.luiscarodev.posticketbridge.action.STOP_BRIDGE"

        fun start(context: Context) {
            if (!hasRequiredLocalNetworkPermission(context)) return
            context.startForegroundService(Intent(context, BridgeForegroundService::class.java))
        }

        fun restart(context: Context) {
            if (!hasRequiredLocalNetworkPermission(context)) {
                stop(context)
                return
            }
            context.startForegroundService(
                Intent(context, BridgeForegroundService::class.java).setAction(ACTION_RESTART),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BridgeForegroundService::class.java))
        }

        internal fun hasRequiredLocalNetworkPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 37 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                PackageManager.PERMISSION_GRANTED
    }
}
