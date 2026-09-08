package com.luiscarodev.posticketbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUiStateTest {
    @Test
    fun freshRequiredPermissionIsPending() {
        assertEquals(
            PermissionUiStatus.PENDING,
            resolvePermissionStatus(
                required = true,
                granted = false,
                requestedBefore = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun deniedPermissionCanBeRequestedAgainWhenRationaleIsAvailable() {
        assertEquals(
            PermissionUiStatus.DENIED,
            resolvePermissionStatus(
                required = true,
                granted = false,
                requestedBefore = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun permanentDenialRequiresSystemSettings() {
        assertEquals(
            PermissionUiStatus.SETTINGS_REQUIRED,
            resolvePermissionStatus(
                required = true,
                granted = false,
                requestedBefore = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun optionalOrGrantedPermissionIsGranted() {
        assertEquals(
            PermissionUiStatus.GRANTED,
            resolvePermissionStatus(false, false, false, false),
        )
        assertEquals(
            PermissionUiStatus.GRANTED,
            resolvePermissionStatus(true, true, true, false),
        )
    }

    @Test
    fun bridgeDependsOnlyOnLocalNetworkPermission() {
        assertFalse(canStartBridge(PermissionUiStatus.PENDING))
        assertFalse(canStartBridge(PermissionUiStatus.DENIED))
        assertFalse(canStartBridge(PermissionUiStatus.SETTINGS_REQUIRED))
        assertTrue(canStartBridge(PermissionUiStatus.GRANTED))
    }
}
