package com.luiscarodev.posticketbridge.ui

enum class PermissionUiStatus {
    GRANTED,
    PENDING,
    DENIED,
    SETTINGS_REQUIRED,
}

data class BridgePermissionUiState(
    val localNetwork: PermissionUiStatus = PermissionUiStatus.PENDING,
    val notifications: PermissionUiStatus = PermissionUiStatus.PENDING,
    val bluetooth: PermissionUiStatus = PermissionUiStatus.PENDING,
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        val Granted = BridgePermissionUiState(
            localNetwork = PermissionUiStatus.GRANTED,
            notifications = PermissionUiStatus.GRANTED,
            bluetooth = PermissionUiStatus.GRANTED,
            onboardingCompleted = true,
        )
    }
}

internal fun resolvePermissionStatus(
    required: Boolean,
    granted: Boolean,
    requestedBefore: Boolean,
    shouldShowRationale: Boolean,
): PermissionUiStatus = when {
    !required || granted -> PermissionUiStatus.GRANTED
    !requestedBefore -> PermissionUiStatus.PENDING
    shouldShowRationale -> PermissionUiStatus.DENIED
    else -> PermissionUiStatus.SETTINGS_REQUIRED
}

internal fun canStartBridge(localNetwork: PermissionUiStatus): Boolean =
    localNetwork == PermissionUiStatus.GRANTED
