package com.luiscarodev.posticketbridge.printing

import android.bluetooth.BluetoothClass
import com.luiscarodev.posticketbridge.domain.BluetoothDeviceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothDeviceKindTest {
    @Test
    fun recognizesReportedAndCommonThermalPrinterIdentities() {
        assertEquals(
            BluetoothDeviceKind.PRINTER,
            bluetoothDeviceKind(BluetoothClass.Device.Major.IMAGING, 0x0680, "Unknown"),
        )
        assertEquals(
            BluetoothDeviceKind.PRINTER,
            bluetoothDeviceKind(BluetoothClass.Device.Major.UNCATEGORIZED, 0x1f00, "Printer001"),
        )
        assertEquals(
            BluetoothDeviceKind.PRINTER,
            bluetoothDeviceKind(BluetoothClass.Device.Major.UNCATEGORIZED, 0x1f00, "MTP-II"),
        )
    }

    @Test
    fun keepsKnownNonPrinterCategoriesDistinct() {
        assertEquals(
            BluetoothDeviceKind.AUDIO,
            bluetoothDeviceKind(BluetoothClass.Device.Major.AUDIO_VIDEO, 0x0418, "Headset"),
        )
        assertEquals(
            BluetoothDeviceKind.PHONE,
            bluetoothDeviceKind(BluetoothClass.Device.Major.PHONE, 0x0200, "Galaxy"),
        )
        assertEquals(
            BluetoothDeviceKind.OTHER,
            bluetoothDeviceKind(null, null, "Unknown device"),
        )
    }
}
