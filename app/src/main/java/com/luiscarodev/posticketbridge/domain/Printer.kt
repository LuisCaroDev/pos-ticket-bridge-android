package com.luiscarodev.posticketbridge.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PrinterType(val wireName: String) {
    @SerialName("network") NETWORK("network"),
    @SerialName("bluetooth") BLUETOOTH("bluetooth"),
    @SerialName("usb") USB("usb"),
}

@Serializable
enum class PrintProfileMode { AUTO, CUSTOM }

@Serializable
enum class PrinterLanguage { ES, EN }

@Serializable
enum class UnicodeFallback { AUTO, RASTER, NATIVE }

@Serializable
enum class NativeTextPolicy { ASCII, ENCODING }

@Serializable
data class PrinterDefinition(
    val id: String,
    val nombre: String,
    val tipo: PrinterType,
    val anchoMm: Int,
    val abreCajon: Boolean,
    val enabled: Boolean,
    val profileId: String = "unlisted-safe",
    val profileMode: PrintProfileMode = PrintProfileMode.AUTO,
    val profileLanguage: PrinterLanguage = PrinterLanguage.ES,
    val customEncoding: String? = null,
    val customCodeTable: Int? = null,
    val customUnicodeFallback: UnicodeFallback? = null,
    val customNativePolicy: NativeTextPolicy? = null,
    val host: String? = null,
    val port: Int? = null,
    val bluetoothAddress: String? = null,
    val bluetoothName: String? = null,
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
    val usbSerialNumber: String? = null,
    val usbName: String? = null,
) : java.io.Serializable

@Serializable
data class PrinterSummary(
    val id: String,
    val nombre: String,
    val tipo: String,
)

fun PrinterDefinition.summary() = PrinterSummary(id, nombre, tipo.wireName)

enum class BluetoothDeviceKind {
    PRINTER, AUDIO, PHONE, COMPUTER, INPUT, NETWORK, WEARABLE, IMAGING, OTHER,
}

data class PairedBluetoothPrinter(
    val name: String,
    val address: String,
    val kind: BluetoothDeviceKind = BluetoothDeviceKind.OTHER,
    val likelyPrinter: Boolean = false,
)

data class NetworkPrinterCandidate(val host: String, val port: Int = 9100)

data class UsbPrinterCandidate(
    val name: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
    val ambiguous: Boolean,
)

interface PrinterCatalog {
    suspend fun getAll(): List<PrinterDefinition>
    suspend fun find(id: String): PrinterDefinition?
}
