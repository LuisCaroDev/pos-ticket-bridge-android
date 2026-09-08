package com.luiscarodev.posticketbridge.printing

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPermissionTest {
    @Test
    fun cancelsDiscoveryOnlyWhenThePlatformPermissionAllowsIt() {
        assertTrue(canCancelBluetoothDiscovery(Build.VERSION_CODES.R, false))
        assertFalse(canCancelBluetoothDiscovery(Build.VERSION_CODES.S, false))
        assertTrue(canCancelBluetoothDiscovery(Build.VERSION_CODES.S, true))
    }
}
