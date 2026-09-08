package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.contract.CutBlock
import com.luiscarodev.posticketbridge.contract.FeedBlock
import com.luiscarodev.posticketbridge.contract.SeparatorBlock
import com.luiscarodev.posticketbridge.contract.TextBlock
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.PrinterType
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileTestPrintJobTest {
    private val printer = PrinterDefinition(
        id = "caja",
        nombre = "Caja 1",
        tipo = PrinterType.NETWORK,
        anchoMm = 80,
        abreCajon = true,
        enabled = true,
        profileLanguage = PrinterLanguage.ES,
        host = "192.168.1.20",
        port = 9100,
    )

    @Test
    fun matchesDesktopSpanishTicketWithMobileBelowHeader() {
        val job = mobileTestPrintJob(printer, printedAt = "8/9/2026, 10:30:00")

        assertEquals(1, job.version)
        assertEquals(80, job.widthMm)
        assertEquals("test", job.reason)
        assertEquals(
            listOf(
                TextBlock("POS TICKET BRIDGE", "center", true, "standard", 2, 2),
                TextBlock("mobile", "center", font = "compact"),
                TextBlock("Prueba de impresión", "center", font = "compact"),
                SeparatorBlock("solid"),
                TextBlock("Impresora: Caja 1"),
                TextBlock(
                    "ASCII: ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 .,:;!?+-*/\n" +
                        "Español: áéíóúüñÑ ÁÉÍÓÚÜ ¿¡\n" +
                        "Símbolos: € $ S/ % # @ & / \\ ( ) [ ] { }",
                ),
                TextBlock("8/9/2026, 10:30:00"),
                FeedBlock(3.0),
                CutBlock(),
            ),
            job.blocks,
        )
    }

    @Test
    fun matchesDesktopEnglishCharacterGroups() {
        val job = mobileTestPrintJob(
            printer.copy(nombre = "Till 1", profileLanguage = PrinterLanguage.EN),
            printedAt = "9/8/2026, 10:30:00 AM",
        )

        assertEquals(
            TextBlock("Print test", "center", font = "compact"),
            job.blocks[2],
        )
        assertEquals(TextBlock("Printer: Till 1"), job.blocks[4])
        assertEquals(
            TextBlock(
                "ASCII: ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 .,:;!?+-*/\n" +
                    "Symbols: € $ S/ % # @ & / \\ ( ) [ ] { }",
            ),
            job.blocks[5],
        )
    }
}
