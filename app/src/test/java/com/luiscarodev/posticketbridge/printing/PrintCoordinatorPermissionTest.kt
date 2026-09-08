package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.domain.PrinterType
import org.junit.Assert.assertEquals
import org.junit.Test

class PrintCoordinatorPermissionTest {
    @Test
    fun mapsSecurityErrorsToTheSpecificTransportPermission() {
        assertEquals("local_network_permission_required", permissionErrorCode(PrinterType.NETWORK))
        assertEquals("bluetooth_permission_required", permissionErrorCode(PrinterType.BLUETOOTH))
        assertEquals("usb_permission_required", permissionErrorCode(PrinterType.USB))
    }
}
