package com.luiscarodev.posticketbridge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterIdTest {
    @Test
    fun `matches desktop slug normalization`() {
        assertEquals("caja-principal", printerIdSlug("  Caja_Principal  "))
        assertEquals("impresora-termica", printerIdSlug("Impresora térmica"))
    }

    @Test
    fun `uses desktop compatible random fallback`() {
        assertTrue(printerIdSlug("¿¡—").matches(Regex("printer-[0-9a-f]{6}")))
    }
}
