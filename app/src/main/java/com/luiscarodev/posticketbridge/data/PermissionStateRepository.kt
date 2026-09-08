package com.luiscarodev.posticketbridge.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.permissionStateDataStore by preferencesDataStore(name = "permission_state")

data class PermissionRequestHistory(
    val localNetworkRequested: Boolean = false,
    val notificationsRequested: Boolean = false,
    val bluetoothRequested: Boolean = false,
    val onboardingCompleted: Boolean = false,
)

enum class RuntimePermissionKind {
    LOCAL_NETWORK,
    NOTIFICATIONS,
    BLUETOOTH,
}

class PermissionStateRepository(private val context: Context) {
    private object Keys {
        val localNetworkRequested = booleanPreferencesKey("local_network_requested")
        val notificationsRequested = booleanPreferencesKey("notifications_requested")
        val bluetoothRequested = booleanPreferencesKey("bluetooth_requested")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }

    val history: Flow<PermissionRequestHistory> = context.permissionStateDataStore.data.map { preferences ->
        PermissionRequestHistory(
            localNetworkRequested = preferences[Keys.localNetworkRequested] ?: false,
            notificationsRequested = preferences[Keys.notificationsRequested] ?: false,
            bluetoothRequested = preferences[Keys.bluetoothRequested] ?: false,
            onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
        )
    }

    suspend fun markRequested(kind: RuntimePermissionKind) {
        context.permissionStateDataStore.edit { preferences ->
            preferences[when (kind) {
                RuntimePermissionKind.LOCAL_NETWORK -> Keys.localNetworkRequested
                RuntimePermissionKind.NOTIFICATIONS -> Keys.notificationsRequested
                RuntimePermissionKind.BLUETOOTH -> Keys.bluetoothRequested
            }] = true
        }
    }

    suspend fun completeOnboarding() {
        context.permissionStateDataStore.edit { it[Keys.onboardingCompleted] = true }
    }
}
