package com.luiscarodev.posticketbridge.printing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import com.luiscarodev.posticketbridge.domain.NetworkPrinterCandidate
import com.luiscarodev.posticketbridge.domain.PairedBluetoothPrinter
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.UsbPrinterCandidate
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

fun interface PortProbe {
    suspend fun isOpen(host: String, port: Int, timeoutMs: Int): Boolean
}

class SocketPortProbe : PortProbe {
    override suspend fun isOpen(host: String, port: Int, timeoutMs: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                true
            }.getOrDefault(false)
        }
}

class PrinterDiscoveryRepository(
    private val context: Context,
    private val bluetooth: BluetoothPrinterAccess,
    private val usb: UsbPrinterAccess,
    private val scanner: NetworkPrinterScanner = NetworkPrinterScanner(SocketPortProbe()),
) {
    private val connectivity get() = context.getSystemService(ConnectivityManager::class.java)

    suspend fun scanNetwork(): List<NetworkPrinterCandidate> {
        check(hasLocalNetworkPermission()) { "local_network_permission_required" }
        val ownAddress = activeLanAddress() ?: error("network_unavailable")
        return scanner.scan(ownAddress.hostAddress.orEmpty())
    }

    fun pairedBluetooth(): List<PairedBluetoothPrinter> = bluetooth.paired()
    fun hasBluetoothPermission() = bluetooth.hasPermission()
    fun isBluetoothEnabled() = bluetooth.isEnabled()
    fun connectedUsb(): List<UsbPrinterCandidate> = usb.connected().also {
        if (it.isEmpty() && usb.hasConnectedDevices()) error("usb_incompatible")
    }
    suspend fun authorizeUsb(deviceName: String): UsbPrinterCandidate = usb.authorize(deviceName)

    suspend fun authorizeSavedUsb(printer: PrinterDefinition): UsbPrinterCandidate {
        require(printer.tipo == PrinterType.USB) { "invalid_usb_device" }
        val matches = connectedUsb().filter {
            it.vendorId == printer.usbVendorId && it.productId == printer.usbProductId
        }
        if (matches.isEmpty()) error("usb_disconnected")

        val candidate = printer.usbSerialNumber?.let { serial ->
            matches.firstOrNull { it.serialNumber == serial }
                ?: matches.singleOrNull()?.let { authorizeUsb(it.deviceName) }
                ?: error("usb_ambiguous_device")
        } ?: matches.singleOrNull()?.let { authorizeUsb(it.deviceName) }
            ?: error("usb_ambiguous_device")

        if (candidate.ambiguous) error("usb_ambiguous_device")
        if (printer.usbSerialNumber != null && candidate.serialNumber != printer.usbSerialNumber) {
            error("usb_disconnected")
        }
        return candidate
    }

    fun hasLocalNetworkPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED

    private fun activeLanAddress(): Inet4Address? {
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        ) return null
        return connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
    }

}

class NetworkPrinterScanner(private val probe: PortProbe) {
    suspend fun scan(ownAddress: String): List<NetworkPrinterCandidate> {
        val semaphore = Semaphore(NETWORK_CONCURRENCY)
        return coroutineScope {
            hostsIn24(ownAddress)
                .filterNot { it == ownAddress }
                .map { host ->
                    async {
                        semaphore.withPermit {
                            if (probe.isOpen(host, ESC_POS_PORT, NETWORK_TIMEOUT_MS)) {
                                NetworkPrinterCandidate(host)
                            } else null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private companion object {
        const val ESC_POS_PORT = 9100
        const val NETWORK_CONCURRENCY = 32
        const val NETWORK_TIMEOUT_MS = 500
    }
}

internal fun hostsIn24(ipv4: String): List<String> {
    val parts = ipv4.split('.').mapNotNull(String::toIntOrNull)
    require(parts.size == 4 && parts.all { it in 0..255 }) { "invalid_ipv4" }
    val prefix = parts.take(3).joinToString(".")
    return (1..254).map { "$prefix.$it" }
}
