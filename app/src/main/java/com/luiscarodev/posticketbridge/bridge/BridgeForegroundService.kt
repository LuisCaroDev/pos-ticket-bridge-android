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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BridgeForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transitionMutex = Mutex()
    private val serverLock = Any()
    private var transitionJob: Job? = null
    private var server: BridgeHttpServer? = null
    @Volatile private var destroyed = false

    private val app: BridgeApplication
        get() = application as BridgeApplication

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground(notification(getString(R.string.bridge_starting)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val restart = intent?.action == ACTION_RESTART
        if (restart || server == null) {
            transitionJob?.cancel()
            transitionJob = scope.launch {
                transitionMutex.withLock {
                    if (restart) stopServer()
                    if (server == null) startServer()
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
        app.runtimeRepository.update(BridgeRuntimeState.Stopped)
        super.onDestroy()
    }

    private suspend fun startServer() {
        app.runtimeRepository.update(BridgeRuntimeState.Starting)
        updateNotification(getString(R.string.bridge_starting))
        runCatching {
            val settings = app.settingsRepository.getOrCreate()
            val nextServer = KtorBridgeHttpServer(
                settings,
                app.printerRepository,
                app.printCoordinator,
            )
            synchronized(serverLock) {
                if (destroyed) return
                nextServer.start()
                server = nextServer
            }
            val hosts = suggestedHosts(settings.port)
            app.runtimeRepository.update(BridgeRuntimeState.Running(hosts, settings.port))
            updateNotification(getString(R.string.bridge_running, settings.port))
        }.onFailure { error ->
            server?.stop()
            server = null
            val reason = error.message ?: error::class.java.simpleName
            app.runtimeRepository.update(BridgeRuntimeState.Failed(reason))
            updateNotification(getString(R.string.bridge_failed))
        }
    }

    private fun stopServer() {
        synchronized(serverLock) {
            runCatching { server?.stop() }
            server = null
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
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
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
