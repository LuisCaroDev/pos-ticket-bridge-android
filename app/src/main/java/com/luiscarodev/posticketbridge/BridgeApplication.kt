package com.luiscarodev.posticketbridge

import android.app.Application
import com.luiscarodev.posticketbridge.bridge.BridgeRuntimeRepository
import com.luiscarodev.posticketbridge.bridge.ConnectionUrlRepository
import com.luiscarodev.posticketbridge.bridge.LocalBridgeClient
import com.luiscarodev.posticketbridge.data.BRIDGE_PORT
import com.luiscarodev.posticketbridge.data.BridgeSettingsRepository
import com.luiscarodev.posticketbridge.data.PrinterDatabase
import com.luiscarodev.posticketbridge.data.PermissionStateRepository
import com.luiscarodev.posticketbridge.data.PrinterRepository
import com.luiscarodev.posticketbridge.printing.AndroidPrintRasterizer
import com.luiscarodev.posticketbridge.printing.BluetoothPrinterAccess
import com.luiscarodev.posticketbridge.printing.EscPosEncoder
import com.luiscarodev.posticketbridge.printing.PrintCoordinator
import com.luiscarodev.posticketbridge.printing.PrinterDiscoveryRepository
import com.luiscarodev.posticketbridge.printing.PrinterTransportFactory
import com.luiscarodev.posticketbridge.printing.UsbPrinterAccess

class BridgeApplication : Application() {
    val settingsRepository by lazy { BridgeSettingsRepository(applicationContext) }
    val permissionStateRepository by lazy { PermissionStateRepository(applicationContext) }
    val runtimeRepository by lazy { BridgeRuntimeRepository() }
    val connectionUrlRepository by lazy { ConnectionUrlRepository(applicationContext, BRIDGE_PORT) }
    val httpsRepository by lazy { com.luiscarodev.posticketbridge.bridge.https.HttpsRepository() }
    val localBridgeClient by lazy { LocalBridgeClient(settingsRepository, httpsRepository) }
    val printerDatabase by lazy { PrinterDatabase.create(applicationContext) }
    val printerRepository by lazy { PrinterRepository(printerDatabase.printerDao()) }
    val bluetoothPrinterAccess by lazy { BluetoothPrinterAccess(applicationContext) }
    val usbPrinterAccess by lazy { UsbPrinterAccess(applicationContext) }
    val printerDiscoveryRepository by lazy {
        PrinterDiscoveryRepository(applicationContext, bluetoothPrinterAccess, usbPrinterAccess)
    }
    val printCoordinator by lazy {
        PrintCoordinator(
            printerRepository,
            EscPosEncoder(AndroidPrintRasterizer()),
            PrinterTransportFactory(bluetoothPrinterAccess, usbPrinterAccess),
        )
    }
}
