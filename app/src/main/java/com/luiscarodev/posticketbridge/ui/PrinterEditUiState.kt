package com.luiscarodev.posticketbridge.ui

import com.luiscarodev.posticketbridge.domain.NativeTextPolicy
import com.luiscarodev.posticketbridge.domain.PrintProfileMode
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.UnicodeFallback
import com.luiscarodev.posticketbridge.domain.PrinterProfileCatalog
import java.io.Serializable

data class PrinterEditUiState(
    val sessionId: String? = null,
    val printerId: String? = null,
    val initialDefinition: PrinterDefinition? = null,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val type: PrinterType = PrinterType.NETWORK,
    val width: Int = 80,
    val enabled: Boolean = true,
    val opensDrawer: Boolean = false,
    val profileId: String = PrinterProfileCatalog.DEFAULT_PROFILE_ID,
    val profileMode: PrintProfileMode = PrintProfileMode.AUTO,
    val profileLanguage: PrinterLanguage = PrinterLanguage.ES,
    val customEncoding: String = "CP850",
    val customCodeTable: String = "2",
    val unicodeFallback: UnicodeFallback = UnicodeFallback.AUTO,
    val host: String = "",
    val port: String = "9100",
    val bluetoothAddress: String = "",
    val bluetoothName: String = "",
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
    val usbSerialNumber: String? = null,
    val usbName: String = "",
) : Serializable {
    val editing: Boolean get() = printerId != null
    val dirty: Boolean get() = initialDefinition?.let { toDefinition() != it } ?: false

    val profileValid: Boolean
        get() = profileMode != PrintProfileMode.CUSTOM || customCodeTable.toIntOrNull() in 0..255

    val connectionValid: Boolean
        get() = when (type) {
            PrinterType.NETWORK -> host.isNotBlank() && port.toIntOrNull() in 1..65535
            PrinterType.BLUETOOTH -> bluetoothAddress.isNotBlank()
            PrinterType.USB -> usbVendorId in 0..65535 && usbProductId in 0..65535
        }

    fun toDefinition(): PrinterDefinition = PrinterDefinition(
        id = printerId.orEmpty(),
        nombre = name.trim(),
        tipo = type,
        anchoMm = width,
        abreCajon = opensDrawer,
        enabled = enabled,
        profileId = profileId,
        profileMode = profileMode,
        profileLanguage = profileLanguage,
        customEncoding = customEncoding.takeIf { profileMode == PrintProfileMode.CUSTOM },
        customCodeTable = customCodeTable.toIntOrNull().takeIf { profileMode == PrintProfileMode.CUSTOM },
        customUnicodeFallback = unicodeFallback.takeIf { profileMode == PrintProfileMode.CUSTOM },
        customNativePolicy = NativeTextPolicy.ENCODING.takeIf { profileMode == PrintProfileMode.CUSTOM },
        host = host.takeIf { type == PrinterType.NETWORK }?.trim(),
        port = port.toIntOrNull().takeIf { type == PrinterType.NETWORK },
        bluetoothAddress = bluetoothAddress.takeIf { type == PrinterType.BLUETOOTH },
        bluetoothName = bluetoothName.takeIf { type == PrinterType.BLUETOOTH },
        usbVendorId = usbVendorId.takeIf { type == PrinterType.USB },
        usbProductId = usbProductId.takeIf { type == PrinterType.USB },
        usbSerialNumber = usbSerialNumber.takeIf { type == PrinterType.USB },
        usbName = usbName.takeIf { type == PrinterType.USB },
    )

    companion object {
        fun newPrinter(sessionId: String): PrinterEditUiState = PrinterEditUiState(
            sessionId = sessionId,
            loaded = true,
        )

        fun loading(sessionId: String, printerId: String): PrinterEditUiState = PrinterEditUiState(
            sessionId = sessionId,
            printerId = printerId,
        )

        fun notFound(sessionId: String, printerId: String): PrinterEditUiState = PrinterEditUiState(
            sessionId = sessionId,
            printerId = printerId,
            loaded = true,
            notFound = true,
        )

        fun from(sessionId: String, printer: PrinterDefinition): PrinterEditUiState {
            val state = PrinterEditUiState(
                sessionId = sessionId,
                printerId = printer.id,
                loaded = true,
                name = printer.nombre,
                type = printer.tipo,
                width = printer.anchoMm,
                enabled = printer.enabled,
                opensDrawer = printer.abreCajon,
                profileId = printer.profileId,
                profileMode = printer.profileMode,
                profileLanguage = printer.profileLanguage,
                customEncoding = printer.customEncoding ?: if (printer.profileLanguage == PrinterLanguage.ES) "CP850" else "CP437",
                customCodeTable = (printer.customCodeTable ?: if (printer.profileLanguage == PrinterLanguage.ES) 2 else 0).toString(),
                unicodeFallback = printer.customUnicodeFallback ?: UnicodeFallback.AUTO,
                host = printer.host.orEmpty(),
                port = (printer.port ?: 9100).toString(),
                bluetoothAddress = printer.bluetoothAddress.orEmpty(),
                bluetoothName = printer.bluetoothName.orEmpty(),
                usbVendorId = printer.usbVendorId,
                usbProductId = printer.usbProductId,
                usbSerialNumber = printer.usbSerialNumber,
                usbName = printer.usbName.orEmpty(),
            )
            return state.copy(initialDefinition = state.toDefinition())
        }
    }
}
