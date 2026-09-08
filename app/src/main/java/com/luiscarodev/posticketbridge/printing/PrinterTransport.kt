package com.luiscarodev.posticketbridge.printing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.luiscarodev.posticketbridge.domain.PairedBluetoothPrinter
import com.luiscarodev.posticketbridge.domain.BluetoothDeviceKind
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.UsbPrinterCandidate
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface PrinterTransport {
    suspend fun write(bytes: ByteArray)
}

class TcpPrinterTransport(private val host: String, private val port: Int) : PrinterTransport {
    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = IO_TIMEOUT_MS
            socket.getOutputStream().apply { write(bytes); flush() }
        }
        Unit
    }

    private companion object { const val CONNECT_TIMEOUT_MS = 5_000; const val IO_TIMEOUT_MS = 10_000 }
}

class BluetoothPrinterAccess(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    fun paired(): List<PairedBluetoothPrinter> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices.orEmpty().map { device ->
            val name = device.name ?: device.address
            val kind = bluetoothDeviceKind(
                device.bluetoothClass?.majorDeviceClass,
                device.bluetoothClass?.deviceClass,
                name,
            )
            PairedBluetoothPrinter(name, device.address, kind, kind == BluetoothDeviceKind.PRINTER)
        }.sortedWith(compareByDescending<PairedBluetoothPrinter> { it.likelyPrinter }.thenBy { it.name.lowercase() })
    }

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun isEnabled(): Boolean = hasPermission() && adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun transport(address: String): PrinterTransport = PrinterTransport { bytes ->
        withContext(Dispatchers.IO) {
            check(hasPermission()) { "bluetooth_permission_required" }
            val bluetoothAdapter = adapter ?: error("bluetooth_unavailable")
            check(bluetoothAdapter.isEnabled) { "bluetooth_disabled" }
            if (canCancelBluetoothDiscovery(
                    Build.VERSION.SDK_INT,
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            ) {
                bluetoothAdapter.cancelDiscovery()
            }
            val device = bluetoothAdapter.getRemoteDevice(address)
            val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            val socket = if (runCatching { secureSocket.connect() }.isSuccess) secureSocket else {
                runCatching { secureSocket.close() }
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
            }
            socket.use {
                it.outputStream.apply { write(bytes); flush() }
                Thread.sleep(300)
            }
        }
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

internal fun canCancelBluetoothDiscovery(sdkInt: Int, scanPermissionGranted: Boolean): Boolean =
    sdkInt < Build.VERSION_CODES.S || scanPermissionGranted

internal fun bluetoothDeviceKind(majorClass: Int?, deviceClass: Int?, name: String): BluetoothDeviceKind {
    val reportedPrinter = majorClass == BluetoothClass.Device.Major.IMAGING &&
        deviceClass?.and(IMAGING_PRINTER_MASK) != 0
    val nameLooksLikePrinter = PRINTER_NAME_HINT.containsMatchIn(name)
    if (reportedPrinter || nameLooksLikePrinter) return BluetoothDeviceKind.PRINTER
    return when (majorClass) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> BluetoothDeviceKind.AUDIO
        BluetoothClass.Device.Major.PHONE -> BluetoothDeviceKind.PHONE
        BluetoothClass.Device.Major.COMPUTER -> BluetoothDeviceKind.COMPUTER
        BluetoothClass.Device.Major.PERIPHERAL -> BluetoothDeviceKind.INPUT
        BluetoothClass.Device.Major.NETWORKING -> BluetoothDeviceKind.NETWORK
        BluetoothClass.Device.Major.WEARABLE -> BluetoothDeviceKind.WEARABLE
        BluetoothClass.Device.Major.IMAGING -> BluetoothDeviceKind.IMAGING
        else -> BluetoothDeviceKind.OTHER
    }
}

private const val IMAGING_PRINTER_MASK = 0x80
private val PRINTER_NAME_HINT = Regex(
    "(?:printer|xprinter|gprinter|thermal|rongta|zjiang|pos[-_ ]|mtp[-_ ]|rpp?[-_0-9]|xp[-_0-9])",
    RegexOption.IGNORE_CASE,
)

class UsbPrinterAccess(private val context: Context) {
    private val manager get() = context.getSystemService(UsbManager::class.java)

    fun connected(): List<UsbPrinterCandidate> {
        val devices = manager?.deviceList?.values.orEmpty().filter { findBulkOut(it) != null }
        return devices.map { device ->
            val authorized = manager?.hasPermission(device) == true
            val serial = if (authorized) runCatching { device.serialNumber }.getOrNull() else null
            val identical = devices.count { it.vendorId == device.vendorId && it.productId == device.productId }
            UsbPrinterCandidate(
                name = runCatching { device.productName }.getOrNull()
                    ?: runCatching { device.manufacturerName }.getOrNull()
                    ?: "USB ${hex(device.vendorId)}:${hex(device.productId)}",
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                serialNumber = serial,
                ambiguous = authorized && serial == null && identical > 1,
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun hasConnectedDevices(): Boolean = manager?.deviceList?.isNotEmpty() == true

    suspend fun authorize(deviceName: String): UsbPrinterCandidate {
        val usbManager = manager ?: error("usb_unavailable")
        val device = usbManager.deviceList[deviceName] ?: error("usb_disconnected")
        if (!usbManager.hasPermission(device) && !requestPermission(usbManager, device)) {
            error("usb_permission_denied")
        }
        return connected().firstOrNull { it.deviceName == deviceName } ?: error("usb_disconnected")
    }

    fun transport(printer: PrinterDefinition): PrinterTransport = PrinterTransport { bytes ->
        withContext(Dispatchers.IO) {
            val usbManager = manager ?: error("usb_unavailable")
            val matches = usbManager.deviceList.values.filter {
                it.vendorId == printer.usbVendorId && it.productId == printer.usbProductId && findBulkOut(it) != null
            }
            val device = if (printer.usbSerialNumber != null) {
                matches.firstOrNull {
                    usbManager.hasPermission(it) && runCatching { it.serialNumber }.getOrNull() == printer.usbSerialNumber
                } ?: error("usb_disconnected")
            } else {
                if (matches.size > 1) error("usb_ambiguous_device")
                matches.singleOrNull() ?: error("usb_disconnected")
            }
            check(usbManager.hasPermission(device)) { "usb_permission_required" }
            val (usbInterface, endpoint) = findBulkOut(device) ?: error("usb_incompatible")
            val connection = usbManager.openDevice(device) ?: error("usb_open_failed")
            try {
                check(connection.claimInterface(usbInterface, true)) { "usb_claim_failed" }
                writeUsbChunks(bytes) { offset, length ->
                    connection.bulkTransfer(endpoint, bytes, offset, length, USB_TIMEOUT_MS)
                }
            } finally {
                runCatching { connection.releaseInterface(usbInterface) }
                connection.close()
            }
        }
    }

    private suspend fun requestPermission(usbManager: UsbManager, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuation ->
            val action = "${context.packageName}.USB_PERMISSION.${REQUEST_IDS.incrementAndGet()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    if (intent?.action != action || !continuation.isActive) return
                    runCatching { context.unregisterReceiver(this) }
                    continuation.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_IDS.get(),
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            try {
                usbManager.requestPermission(device, pendingIntent)
            } catch (error: Throwable) {
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    private fun findBulkOut(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        repeat(device.interfaceCount) { interfaceIndex ->
            val usbInterface = device.getInterface(interfaceIndex)
            repeat(usbInterface.endpointCount) { endpointIndex ->
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.direction == UsbConstants.USB_DIR_OUT &&
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                ) return usbInterface to endpoint
            }
        }
        return null
    }

    private fun hex(value: Int) = "0x%04x".format(value)

    private companion object {
        const val USB_CHUNK_SIZE = 16 * 1024
        const val USB_TIMEOUT_MS = 10_000
        val REQUEST_IDS = AtomicInteger()
    }
}

internal fun writeUsbChunks(
    bytes: ByteArray,
    chunkSize: Int = 16 * 1024,
    write: (offset: Int, length: Int) -> Int,
) {
    var offset = 0
    while (offset < bytes.size) {
        val written = write(offset, minOf(chunkSize, bytes.size - offset))
        check(written > 0) { "usb_write_failed" }
        offset += written
    }
}

class PrinterTransportFactory(
    private val bluetooth: BluetoothPrinterAccess,
    private val usb: UsbPrinterAccess,
) {
    fun create(printer: PrinterDefinition): PrinterTransport = when (printer.tipo) {
        PrinterType.NETWORK -> TcpPrinterTransport(printer.host!!, printer.port ?: 9100)
        PrinterType.BLUETOOTH -> bluetooth.transport(printer.bluetoothAddress!!)
        PrinterType.USB -> usb.transport(printer)
    }
}
