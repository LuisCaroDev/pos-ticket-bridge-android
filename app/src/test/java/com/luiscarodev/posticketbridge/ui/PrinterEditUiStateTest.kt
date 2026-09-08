package com.luiscarodev.posticketbridge.ui

import com.luiscarodev.posticketbridge.domain.PrintProfileMode
import com.luiscarodev.posticketbridge.domain.NativeTextPolicy
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.UnicodeFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterEditUiStateTest {
    @Test
    fun persistedPrinterRoundTripsThroughSingleFormState() {
        val printer = PrinterDefinition(
            id = "caja",
            nombre = "Caja",
            tipo = PrinterType.USB,
            anchoMm = 58,
            abreCajon = true,
            enabled = true,
            profileId = "epson-escpos-usb",
            profileMode = PrintProfileMode.CUSTOM,
            profileLanguage = PrinterLanguage.ES,
            customEncoding = "CP850",
            customCodeTable = 2,
            customUnicodeFallback = UnicodeFallback.RASTER,
            customNativePolicy = NativeTextPolicy.ENCODING,
            usbVendorId = 0x04b8,
            usbProductId = 0x0202,
            usbSerialNumber = "serial",
            usbName = "Epson",
        )

        val state = PrinterEditUiState.from("session", printer)

        assertTrue(state.loaded)
        assertTrue(state.editing)
        assertFalse(state.dirty)
        assertEquals(printer, state.toDefinition())
        assertTrue(state.copy(name = "Caja principal").dirty)
        assertFalse(state.copy(name = "Caja principal").copy(name = state.name).dirty)
    }
}
