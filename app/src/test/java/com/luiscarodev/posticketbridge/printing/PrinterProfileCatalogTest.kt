package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.domain.NativeTextPolicy
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.PrinterProfileCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterProfileCatalogTest {
    @Test
    fun catalogKeepsVerifiedDesktopProfilesAndEffectiveEncodings() {
        assertEquals(
            listOf("unlisted-safe", "epson-escpos-usb", "xprinter-xp-e260l"),
            PrinterProfileCatalog.profiles.map { it.id },
        )

        val epsonSpanish = PrinterProfileCatalog.get("epson-escpos-usb")
            .encodingFor(PrinterLanguage.ES)
        assertEquals("CP850", epsonSpanish.encoding)
        assertEquals("IBM850", epsonSpanish.charsetName)
        assertEquals(2, epsonSpanish.codeTable)
        assertEquals(NativeTextPolicy.ENCODING, epsonSpanish.nativePolicy)

        val xprinterSpanish = PrinterProfileCatalog.get("xprinter-xp-e260l")
            .encodingFor(PrinterLanguage.ES)
        assertEquals("CP858", xprinterSpanish.encoding)
        assertEquals(19, xprinterSpanish.codeTable)
    }

    @Test
    fun catalogControlsWidthAndUsbSuggestions() {
        assertEquals(
            listOf("unlisted-safe", "epson-escpos-usb"),
            PrinterProfileCatalog.selectable(58).map { it.id },
        )
        assertTrue(PrinterProfileCatalog.selectable(80).any { it.id == "xprinter-xp-e260l" })
        assertEquals(
            "epson-escpos-usb",
            PrinterProfileCatalog.suggestedForUsb(0x04b8, 0x0202)?.id,
        )
        assertNull(PrinterProfileCatalog.suggestedForUsb(0x1234, 0x5678))
    }

    @Test
    fun unknownIdsResolveToSafeProfile() {
        assertEquals(
            PrinterProfileCatalog.DEFAULT_PROFILE_ID,
            PrinterProfileCatalog.get("unknown").id,
        )
    }
}
