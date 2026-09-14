package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.contract.BarcodeBlock
import com.luiscarodev.posticketbridge.contract.CutBlock
import com.luiscarodev.posticketbridge.contract.FeedBlock
import com.luiscarodev.posticketbridge.contract.ImageBlock
import com.luiscarodev.posticketbridge.contract.OpenDrawerBlock
import com.luiscarodev.posticketbridge.contract.PrintJobV1
import com.luiscarodev.posticketbridge.contract.QrBlock
import com.luiscarodev.posticketbridge.contract.SeparatorBlock
import com.luiscarodev.posticketbridge.contract.TableRowBlock
import com.luiscarodev.posticketbridge.contract.TextBlock
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.NativeTextPolicy
import com.luiscarodev.posticketbridge.domain.PrintProfileMode
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.UnicodeFallback
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosEncoderTest {
    private val rasterizer = object : PrintRasterizer {
        override suspend fun image(source: String, maxWidth: Int, maxHeight: Int?) =
            RasterImage(1, 1, booleanArrayOf(true))
        override fun text(block: TextBlock, maxWidth: Int) = RasterImage(1, 1, booleanArrayOf(true))
    }
    private val encoder = EscPosEncoder(rasterizer)
    private val printer = PrinterDefinition(
        "caja", "Caja", PrinterType.NETWORK, 80, true, true,
        profileId = "epson-escpos-usb", host = "127.0.0.1", port = 9100,
    )

    @Test
    fun mapsSemanticFontsToEscMAndRestoresFontA() = runBlocking {
        val bytes = encoder.encode(
            PrintJobV1(1, blocks = listOf(
                TextBlock("A", font = "standard"),
                TextBlock("B", font = "compact"),
                TextBlock("C", font = "compact-tall"),
            )),
            printer,
        )
        assertTrue(bytes.containsSequence(0x1B, 0x4D, 0))
        assertTrue(bytes.containsSequence(0x1B, 0x4D, 1))
        assertTrue(bytes.containsSequence(0x1B, 0x4D, 2))
        assertArrayEquals(
            byteArrayOf(0x1B, 0x4D, 0, 0x1D, 0x21, 0),
            bytes.takeLast(6).toByteArray(),
        )
    }

    @Test
    fun encodesEveryContractBlockAndDrawer() = runBlocking {
        val bytes = encoder.encode(
            PrintJobV1(1, blocks = listOf(
                ImageBlock("data:image/png;base64,x"),
                TextBlock("Ticket", "center", true, "compact", 2, 2, true),
                TableRowBlock("Total", "20.00", true, "right"),
                SeparatorBlock("dotted"), FeedBlock(2.0), QrBlock("hello", 4),
                BarcodeBlock("123456789012", "EAN13"), CutBlock(true), OpenDrawerBlock,
            )),
            printer,
        )
        assertTrue(bytes.containsSequence(0x1B, 0x33, 0, 0x1B, 0x2A, 0x21))
        assertTrue(bytes.containsSequence(0x1D, 0x5A, 2, 0x1B, 0x5A, 3))
        assertTrue(bytes.containsSequence(0x1D, 0x6B, 2))
        assertTrue(bytes.containsSequence(0x1D, 0x56, 1))
        assertTrue(bytes.containsSequence(0x1B, 0x70, 0, 25, 120))
    }

    @Test
    fun advancesPaperBeforeCutting() = runBlocking {
        val bytes = encoder.encode(PrintJobV1(1, blocks = listOf(CutBlock(true))), printer)

        assertTrue(bytes.containsSequence(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidEanBeforeWriting() {
        runBlocking {
            encoder.encode(PrintJobV1(1, blocks = listOf(BarcodeBlock("abc", "EAN13"))), printer)
        }
    }

    @Test
    fun customProfileUsesConfiguredCodeTableAndEncoding() = runBlocking {
        val custom = printer.copy(
            profileMode = PrintProfileMode.CUSTOM,
            customEncoding = "CP858",
            customCodeTable = 19,
            customUnicodeFallback = UnicodeFallback.AUTO,
            customNativePolicy = NativeTextPolicy.ENCODING,
        )
        val bytes = encoder.encode(PrintJobV1(1, blocks = listOf(TextBlock("á"))), custom)
        assertTrue(bytes.containsSequence(0x1B, 0x74, 19))
        assertTrue(bytes.containsSequence("á".toByteArray(Charset.forName("IBM00858"))[0].toInt() and 0xff))
    }

    @Test
    fun rasterStrategyForcesBitmapFallback() = runBlocking {
        val custom = printer.copy(
            profileMode = PrintProfileMode.CUSTOM,
            customEncoding = "CP850",
            customCodeTable = 2,
            customUnicodeFallback = UnicodeFallback.RASTER,
        )
        val bytes = encoder.encode(PrintJobV1(1, blocks = listOf(TextBlock("ASCII"))), custom)
        assertTrue(bytes.containsSequence(0x1D, 0x76, 0x30, 0))
    }

    @Test
    fun xprinterSpanishTextUsesNativeCp858InsteadOfRaster() = runBlocking {
        val xprinter = printer.copy(
            profileId = "xprinter-xp-e260l",
            profileLanguage = PrinterLanguage.ES,
            abreCajon = true,
        )

        val bytes = encoder.encode(
            PrintJobV1(1, blocks = listOf(TextBlock("Atendió: José"))),
            xprinter,
        )

        assertTrue(bytes.containsSequence(0x41, 0x74, 0x65, 0x6E, 0x64, 0x69, 0xA2))
        assertTrue(bytes.containsSequence(0x4A, 0x6F, 0x73, 0x82))
        assertTrue(!bytes.containsSequence(0x1D, 0x76, 0x30, 0))
    }

    @Test
    fun xprinterSpanishReceiptCharactersStayNative() = runBlocking {
        val xprinter = printer.copy(
            profileId = "xprinter-xp-e260l",
            profileLanguage = PrinterLanguage.ES,
        )

        val bytes = encoder.encode(
            PrintJobV1(1, blocks = listOf(
                TextBlock("** REIMPRESIÓN **", align = "center", bold = true),
                TextBlock("Atendió: luisangelcaroocio@gmail.com"),
                TextBlock("Caja: Caja luis"),
                TableRowBlock("1x Arroz con pollo · personal", "S/ 320.00", bold = true),
                TableRowBlock("1x Limonada · para compartir", "S/ 25.00", bold = true),
                TableRowBlock("Subtotal", "S/ 195.00"),
                TableRowBlock("TOTAL", "S/ 195.00", bold = true),
            )),
            xprinter,
        )

        assertTrue(!bytes.containsSequence(0x1D, 0x76, 0x30, 0))
    }

    @Test
    fun matchesDesktopBytesForXprinterSpanishReceiptBlocks() = runBlocking {
        val xprinter = printer.copy(
            profileId = "xprinter-xp-e260l",
            profileLanguage = PrinterLanguage.ES,
        )
        val bytes = encoder.encode(
            PrintJobV1(1, widthMm = 80, reason = "receipt", blocks = listOf(
                TextBlock("Atendió: José", font = "standard"),
                TextBlock("Fuente B", font = "compact"),
                TextBlock("Fuente C", font = "compact-tall"),
                TableRowBlock("Subtotal", "S/ 195.00"),
                TableRowBlock("TOTAL", "S/ 195.00", bold = true),
                SeparatorBlock("dotted"),
                FeedBlock(2.0),
                QrBlock("hello", 4),
                BarcodeBlock("123456789012", "EAN13"),
                BarcodeBlock("ABC123", "CODE128"),
                OpenDrawerBlock,
                CutBlock(true),
            )),
            xprinter,
        )
        val desktopHex = javaClass.classLoader!!
            .getResource("escpos-xprinter-spanish-desktop.hex")!!
            .readText()
            .filterNot(Char::isWhitespace)

        assertArrayEquals(desktopHex.hexToByteArray(), bytes)
    }

    @Test
    fun centersImageBlocksAndMatchesDesktopBitmapCommand() = runBlocking {
        val bytes = encoder.encode(
            PrintJobV1(1, blocks = listOf(ImageBlock("data:image/png;base64,x"))),
            printer,
        )

        assertArrayEquals(
            "1b401c2e1b74021b61011b33001b2a2101008000000a1b32".hexToByteArray(),
            bytes,
        )
    }

    private fun ByteArray.containsSequence(vararg expected: Int): Boolean =
        indices.any { start -> expected.indices.all { offset -> getOrNull(start + offset)?.toInt()?.and(0xFF) == expected[offset] } }

    private fun String.hexToByteArray(): ByteArray = chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
