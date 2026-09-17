package com.luiscarodev.posticketbridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.luiscarodev.posticketbridge.bridge.BridgeForegroundService
import com.luiscarodev.posticketbridge.data.PermissionRequestHistory
import com.luiscarodev.posticketbridge.data.RuntimePermissionKind
import com.luiscarodev.posticketbridge.ui.BridgeApp
import com.luiscarodev.posticketbridge.ui.BatteryUiState
import com.luiscarodev.posticketbridge.ui.BridgePermissionUiState
import com.luiscarodev.posticketbridge.ui.PermissionUiStatus
import com.luiscarodev.posticketbridge.ui.resolvePermissionStatus
import com.luiscarodev.posticketbridge.ui.theme.POSTicketBridgeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var afterBluetoothPermission: (() -> Unit)? = null
    private var requestHistory = PermissionRequestHistory()
    private val permissionState = MutableStateFlow(BridgePermissionUiState())
    private val batteryState = MutableStateFlow(BatteryUiState.Unrestricted)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        markRequested(RuntimePermissionKind.NOTIFICATIONS)
        refreshPermissionState()
    }
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        markRequested(RuntimePermissionKind.LOCAL_NETWORK)
        refreshPermissionState()
        if (granted) BridgeForegroundService.start(this) else BridgeForegroundService.stop(this)
    }
    private val bluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        markRequested(RuntimePermissionKind.BLUETOOTH)
        refreshPermissionState()
        if (granted) afterBluetoothPermission?.invoke()
        afterBluetoothPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            (application as BridgeApplication).permissionStateRepository.history.collect { history ->
                requestHistory = history
                refreshPermissionState()
            }
        }
        refreshPermissionState()
        refreshBatteryState()

        enableEdgeToEdge()
        setContent {
            POSTicketBridgeTheme {
                BridgeApp(
                    permissionState = permissionState,
                    batteryState = batteryState,
                    onRequestLocalNetworkPermission = ::requestLocalNetworkPermission,
                    onRequestNotificationPermission = ::requestNotificationPermission,
                    onOpenAppSettings = ::openAppSettings,
                    onOpenBatterySaverSettings = ::openBatterySaverSettings,
                    onOpenBatteryOptimizationSettings = ::openBatteryOptimizationSettings,
                    onCompletePermissionSetup = ::completePermissionSetup,
                    onRequestBluetoothPermission = { afterGranted ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            afterBluetoothPermission = afterGranted
                            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else afterGranted()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasLocalNetworkPermission()) {
            BridgeForegroundService.start(this)
        } else {
            BridgeForegroundService.stop(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshBatteryState()
        // Closing the notification shade must not undo its Stop action.
        if (!hasLocalNetworkPermission()) BridgeForegroundService.stop(this)
    }

    private fun requestLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT >= 37) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            BridgeForegroundService.start(this)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun markRequested(kind: RuntimePermissionKind) {
        requestHistory = when (kind) {
            RuntimePermissionKind.LOCAL_NETWORK -> requestHistory.copy(localNetworkRequested = true)
            RuntimePermissionKind.NOTIFICATIONS -> requestHistory.copy(notificationsRequested = true)
            RuntimePermissionKind.BLUETOOTH -> requestHistory.copy(bluetoothRequested = true)
        }
        lifecycleScope.launch {
            (application as BridgeApplication).permissionStateRepository.markRequested(kind)
        }
    }

    private fun completePermissionSetup() {
        requestHistory = requestHistory.copy(onboardingCompleted = true)
        refreshPermissionState()
        lifecycleScope.launch {
            (application as BridgeApplication).permissionStateRepository.completeOnboarding()
        }
    }

    private fun refreshPermissionState() {
        val localNetwork = permissionStatus(
            permission = if (Build.VERSION.SDK_INT >= 37) Manifest.permission.ACCESS_LOCAL_NETWORK else null,
            requestedBefore = requestHistory.localNetworkRequested,
        )
        val notifications = permissionStatus(
            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else null,
            requestedBefore = requestHistory.notificationsRequested,
        )
        val bluetooth = permissionStatus(
            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else null,
            requestedBefore = requestHistory.bluetoothRequested,
        )
        permissionState.value = BridgePermissionUiState(
            localNetwork = localNetwork,
            notifications = notifications,
            bluetooth = bluetooth,
            onboardingCompleted = requestHistory.onboardingCompleted,
        )
    }

    private fun permissionStatus(permission: String?, requestedBefore: Boolean): PermissionUiStatus {
        if (permission == null) return PermissionUiStatus.GRANTED
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        return resolvePermissionStatus(
            required = true,
            granted = granted,
            requestedBefore = requestedBefore,
            shouldShowRationale = shouldShowRequestPermissionRationale(permission),
        )
    }

    private fun hasLocalNetworkPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED

    private fun refreshBatteryState() {
        val powerManager = getSystemService(PowerManager::class.java)
        batteryState.value = BatteryUiState(
            isPowerSaveMode = powerManager.isPowerSaveMode,
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName),
        )
    }

    private fun openBatterySaverSettings() {
        openSettings(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }

    private fun openBatteryOptimizationSettings() {
        openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.getOrElse { openAppSettings() }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }
}
