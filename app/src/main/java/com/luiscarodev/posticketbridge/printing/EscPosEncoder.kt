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
import com.luiscarodev.posticketbridge.domain.NativeTextPolicy
import com.luiscarodev.posticketbridge.domain.PrintProfileMode
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.UnicodeFallback
import com.luiscarodev.posticketbridge.domain.CatalogPrinterProfile
import com.luiscarodev.posticketbridge.domain.PrinterProfileCatalog
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import kotlin.math.ceil

data class EscPosProfile(
    val id: String,
    val charset: Charset,
    val codeTable: Int,
    val asciiOnly: Boolean,
    val unicodeFallback: UnicodeFallback,
    val rasterWidth: Int,
    val columns: Int,
)

object EscPosProfiles {
    val ids: List<String> = PrinterProfileCatalog.profiles.map(CatalogPrinterProfile::id)
    val encodings = linkedMapOf(
        "CP437" to "IBM437",
        "CP850" to "IBM850",
        "CP860" to "IBM860",
        "WINDOWS-1252" to "windows-1252",
        "CP858" to "IBM00858",
    )

    fun defaultCodeTable(encoding: String): Int = when (encoding) {
        "CP437" -> 0
        "CP850" -> 2
        "CP860" -> 3
        "WINDOWS-1252" -> 16
        "CP858" -> 19
        else -> 0
    }

    fun resolve(printer: PrinterDefinition): EscPosProfile {
        val dimensions = if (printer.anchoMm == 58) 384 to 32 else 576 to 48
        if (printer.profileMode == PrintProfileMode.CUSTOM) {
            val encoding = printer.customEncoding?.uppercase() ?: "CP850"
            return EscPosProfile(
                id = "custom",
                charset = Charset.forName(encodings[encoding] ?: encoding),
                codeTable = printer.customCodeTable ?: 2,
                asciiOnly = printer.customNativePolicy == NativeTextPolicy.ASCII,
                unicodeFallback = printer.customUnicodeFallback ?: UnicodeFallback.AUTO,
                rasterWidth = dimensions.first,
                columns = dimensions.second,
            )
        }
        val catalogEncoding = PrinterProfileCatalog.get(printer.profileId)
            .encodingFor(printer.profileLanguage)
        return EscPosProfile(
            printer.profileId,
            Charset.forName(catalogEncoding.charsetName),
            catalogEncoding.codeTable,
            catalogEncoding.nativePolicy == NativeTextPolicy.ASCII,
            UnicodeFallback.AUTO,
            dimensions.first,
            dimensions.second,
        )
    }
}

data class RasterImage(val width: Int, val height: Int, val blackPixels: BooleanArray) {
    init { require(width > 0 && height > 0 && blackPixels.size == width * height) }
}

interface PrintRasterizer {
    suspend fun image(source: String, maxWidth: Int, maxHeight: Int?): RasterImage
    fun text(block: TextBlock, maxWidth: Int): RasterImage
}

class EscPosEncoder(private val rasterizer: PrintRasterizer) {
    suspend fun encode(job: PrintJobV1, printer: PrinterDefinition): ByteArray {
        validateBeforeTransport(job)
        val profile = EscPosProfiles.resolve(printer)
        return ByteArrayOutputStream().apply {
            command(ESC, 0x40)
            command(0x1C, 0x2E)
            command(ESC, 0x74, profile.codeTable)
            job.blocks.forEach { block ->
                when (block) {
                    is TextBlock -> text(block, profile)
                    is TableRowBlock -> tableRow(block, profile)
                    is SeparatorBlock -> raw(((if (block.style == "dotted") "." else "-").repeat(profile.columns) + "\n").toByteArray(profile.charset))
                    is FeedBlock -> repeat((block.lines?.toInt() ?: 1).coerceIn(0, 100)) { write('\n'.code) }
                    is ImageBlock -> {
                        val maxWidth = (block.maxWidth?.toInt() ?: profile.rasterWidth)
                            .coerceIn(1, profile.rasterWidth)
                        command(ESC, 0x61, 1)
                        bitmap(rasterizer.image(
                            block.url,
                            maxWidth,
                            (block.maxHeight?.toInt() ?: maxWidth).coerceAtLeast(1),
                        ))
                    }
                    is QrBlock -> qr(block)
                    is BarcodeBlock -> barcode(block)
                    is CutBlock -> cut(block.partial)
                    OpenDrawerBlock -> if (printer.abreCajon) drawer()
                }
            }
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.text(block: TextBlock, profile: EscPosProfile) {
        val native = canEncode(block.content, profile)
        if (!native) {
            raster(rasterizer.text(block, profile.rasterWidth))
            return
        }
        command(ESC, 0x61, alignment(block.align))
        style(block.bold, block.underline)
        font(block.font)
        size(block.width, block.height)
        raw(block.content.toByteArray(profile.charset))
        write('\n'.code)
        resetTextFormatting()
    }

    private fun ByteArrayOutputStream.tableRow(block: TableRowBlock, profile: EscPosProfile) {
        if (!canEncode(block.left + block.right, profile)) {
            val leftWidth = (profile.columns * TABLE_LEFT_RATIO).toInt().coerceAtLeast(1)
            val rightWidth = profile.columns - leftWidth
            val line = block.left.take(leftWidth).padEnd(leftWidth) +
                block.right.takeLast(rightWidth).padStart(rightWidth)
            raster(rasterizer.text(TextBlock(line, bold = block.bold), profile.rasterWidth))
            return
        }

        val leftWidth = profile.columns * TABLE_LEFT_RATIO
        val rightWidth = profile.columns * TABLE_RIGHT_RATIO
        var left = block.left
        var right = block.right
        do {
            val leftPart = desktopSubstring(left, end = leftWidth)
            val rightPart = desktopSubstring(right, end = rightWidth)
            cell(leftPart, leftWidth, block.align ?: "left", block.bold, profile.charset)
            cell(rightPart, rightWidth, "right", block.bold, profile.charset)
            write('\n'.code)
            left = desktopSubstring(left, start = leftWidth)
            right = desktopSubstring(right, start = rightWidth)
        } while (left.isNotEmpty() || right.isNotEmpty())
    }

    private fun ByteArrayOutputStream.qr(block: QrBlock) {
        val data = block.content.toByteArray(Charsets.UTF_8)
        command(ESC, 0x61, 1)
        command(0x1D, 0x5A, 2)
        command(ESC, 0x5A, 3, 'L'.code, block.size, block.content.length and 0xFF, block.content.length shr 8)
        raw(data)
    }

    private fun ByteArrayOutputStream.barcode(block: BarcodeBlock) {
        command(ESC, 0x61, 1)
        command(0x1D, 0x77, 3)
        command(0x1D, 0x68, 80)
        command(0x1D, 0x66, 0)
        command(0x1D, 0x48, 2)
        if (block.format == "EAN13") {
            command(0x1D, 0x6B, 2)
            raw((block.content + ean13Parity(block.content)).toByteArray(Charsets.US_ASCII))
        } else {
            command(0x1D, 0x6B, 73)
            val lengthHex = block.content.length.toString(16)
            if (lengthHex.length % 2 == 0) write(block.content.length and 0xFF)
            raw(block.content.toByteArray(Charsets.UTF_8))
        }
        write(0)
    }

    private fun ByteArrayOutputStream.raster(image: RasterImage) {
        val widthBytes = ceil(image.width / 8.0).toInt()
        command(0x1D, 0x76, 0x30, 0, widthBytes and 0xFF, widthBytes shr 8, image.height and 0xFF, image.height shr 8)
        repeat(image.height) { y ->
            repeat(widthBytes) { byteX ->
                var value = 0
                repeat(8) { bit ->
                    val x = byteX * 8 + bit
                    if (x < image.width && image.blackPixels[y * image.width + x]) value = value or (0x80 shr bit)
                }
                write(value)
            }
        }
    }

    private fun ByteArrayOutputStream.bitmap(image: RasterImage) {
        command(ESC, 0x33, 0)
        val bands = ceil(image.height / 24.0).toInt()
        repeat(bands) { band ->
            command(ESC, 0x2A, 0x21, image.width and 0xFF, image.width shr 8)
            repeat(image.width) { x ->
                repeat(3) { byteY ->
                    var value = 0
                    repeat(8) { bit ->
                        val y = band * 24 + byteY * 8 + bit
                        if (y < image.height && image.blackPixels[y * image.width + x]) {
                            value = value or (0x80 shr bit)
                        }
                    }
                    write(value)
                }
            }
            write('\n'.code)
        }
        command(ESC, 0x32)
    }

    private fun ByteArrayOutputStream.drawer() = command(ESC, 0x70, 0, 25, 120)

    private fun ByteArrayOutputStream.cut(partial: Boolean) {
        repeat(CUT_FEED_LINES) { write('\n'.code) }
        command(0x1D, 0x56, if (partial) 1 else 0)
    }

    private fun ByteArrayOutputStream.cell(
        value: String,
        width: Double,
        alignment: String,
        bold: Boolean,
        charset: Charset,
    ) {
        val remaining = (width - desktopTextLength(value)).coerceAtLeast(0.0)
        val leading = if (alignment == "right") ceil(remaining).toInt()
        else if (alignment == "center") (remaining / 2).toInt() else 0
        val trailing = if (alignment == "left") remaining.toInt()
        else if (alignment == "center") ceil(remaining - leading).toInt() else 0
        repeat(leading) { write(' '.code) }
        if (value.isNotEmpty()) {
            style(bold, underline = false)
            raw(value.toByteArray(charset))
            style(bold = false, underline = false)
        }
        repeat(trailing) { write(' '.code) }
    }

    private fun ByteArrayOutputStream.style(bold: Boolean, underline: Boolean) {
        command(ESC, 0x45, if (bold) 1 else 0)
        command(ESC, 0x34, 0)
        command(ESC, 0x2D, if (underline) 1 else 0)
    }

    private fun ByteArrayOutputStream.font(value: String?) = command(ESC, 0x4D, when (value) {
        "compact" -> 1
        "compact-tall" -> 2
        else -> 0
    })

    private fun ByteArrayOutputStream.size(width: Int, height: Int) =
        command(0x1D, 0x21, ((width - 1) shl 4) or (height - 1))

    private fun ByteArrayOutputStream.resetTextFormatting() {
        style(bold = false, underline = false)
        font("standard")
        size(1, 1)
    }

    private fun ByteArrayOutputStream.command(vararg bytes: Int) = bytes.forEach(::write)
    private fun ByteArrayOutputStream.raw(bytes: ByteArray) = write(bytes)
    private fun alignment(value: String?) = when (value) { "center" -> 1; "right" -> 2; else -> 0 }

    private fun canEncode(value: String, profile: EscPosProfile): Boolean {
        if (profile.unicodeFallback == UnicodeFallback.RASTER) return value.isEmpty()
        if (profile.unicodeFallback == UnicodeFallback.NATIVE) return true
        if (profile.asciiOnly && value.any { it.code !in 0x20..0x7E && it != '\n' && it != '\r' && it != '\t' }) return false
        return profile.charset.newEncoder().canEncode(value)
    }

    private fun validateBeforeTransport(job: PrintJobV1) {
        job.blocks.filterIsInstance<BarcodeBlock>().forEach { block ->
            if (block.format == "EAN13") require(block.content.matches(Regex("\\d{12}"))) { "invalid_barcode" }
            else require(block.content.isNotEmpty() && block.content.all { it.code in 0x20..0x7E }) { "invalid_barcode" }
        }
    }

    private fun ean13Parity(value: String): Int {
        val sum = value.reversed().mapIndexed { index, character ->
            character.digitToInt() * if (index % 2 == 0) 3 else 1
        }.sum()
        return (10 - sum % 10) % 10
    }

    private fun desktopTextLength(value: String): Int =
        value.sumOf { if (it.code > 127) 2 else 1 }

    private fun desktopSubstring(
        value: String,
        start: Double = 0.0,
        end: Double? = null,
    ): String = buildString {
        var accumulated = 0
        value.forEach { character ->
            accumulated += if (character.code > 127) 2 else 1
            if (accumulated > start && (end == null || accumulated <= end)) append(character)
        }
    }

    companion object {
        private const val ESC = 0x1B
        private const val CUT_FEED_LINES = 3
        private const val TABLE_LEFT_RATIO = 0.65
        private const val TABLE_RIGHT_RATIO = 0.35
    }
}
