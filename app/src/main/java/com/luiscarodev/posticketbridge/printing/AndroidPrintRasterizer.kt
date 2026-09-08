package com.luiscarodev.posticketbridge.printing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import com.luiscarodev.posticketbridge.contract.TextBlock
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class AndroidPrintRasterizer : PrintRasterizer {
    override suspend fun image(source: String, maxWidth: Int, maxHeight: Int?): RasterImage {
        val bytes = if (source.startsWith("data:")) decodeDataUrl(source) else download(source)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "image_decode_failed" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS) { "image_too_large" }
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2 ||
            (maxHeight != null && bounds.outHeight / sample > maxHeight * 2)
        ) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        )
            ?: error("image_decode_failed")
        return decoded.useBitmap { bitmap ->
            val scale = min(
                1f,
                min(
                    maxWidth.toFloat() / bitmap.width,
                    maxHeight?.let { it.toFloat() / bitmap.height } ?: 1f,
                ),
            )
            val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            bitmap.toRaster(width, height)
        }
    }

    override fun text(block: TextBlock, maxWidth: Int): RasterImage {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.MONOSPACE, if (block.bold) Typeface.BOLD else Typeface.NORMAL)
            textSize = 20f * block.height
            textScaleX = block.width.toFloat() / block.height.coerceAtLeast(1)
            isUnderlineText = block.underline
        }
        require(block.content.length <= MAX_RASTER_TEXT_CHARS) { "text_too_large" }
        val lines = block.content.replace("\r\n", "\n").split('\n')
            .flatMap { wrapLine(it, paint, maxWidth) }
        val lineHeight = ceil(paint.fontSpacing.toDouble()).toInt().coerceAtLeast(1)
        val bitmapHeight = (lineHeight * lines.size + 8).coerceAtLeast(1)
        require(bitmapHeight <= MAX_RASTER_HEIGHT) { "text_too_large" }
        val bitmap = Bitmap.createBitmap(maxWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            lines.forEachIndexed { index, line ->
                val measured = paint.measureText(line)
                val x = when (block.align) {
                    "center" -> (maxWidth - measured) / 2f
                    "right" -> maxWidth - measured
                    else -> 0f
                }.coerceAtLeast(0f)
                canvas.drawText(line, x, (index + 1) * lineHeight.toFloat(), paint)
            }
            bitmap.toRaster()
        } finally { bitmap.recycle() }
    }

    private fun decodeDataUrl(source: String): ByteArray {
        val marker = ";base64,"
        val index = source.indexOf(marker)
        require(index > 5) { "invalid_image_url" }
        return Base64.decode(source.substring(index + marker.length), Base64.DEFAULT)
            .also { require(it.size <= MAX_IMAGE_BYTES) { "image_too_large" } }
    }

    private fun download(source: String): ByteArray {
        val url = URL(source)
        require(url.protocol == "http" || url.protocol == "https") { "invalid_image_url" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            instanceFollowRedirects = true
        }
        try {
            require(connection.responseCode in 200..299) { "image_download_failed" }
            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= MAX_IMAGE_BYTES) { "image_too_large" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= MAX_IMAGE_BYTES) { "image_too_large" }
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        } finally { connection.disconnect() }
    }

    private fun Bitmap.toRaster(targetWidth: Int = width, targetHeight: Int = height): RasterImage {
        val sourcePixels = IntArray(width * height)
        getPixels(sourcePixels, 0, width, 0, 0, width, height)
        val pixels = if (targetWidth == width && targetHeight == height) sourcePixels else {
            IntArray(targetWidth * targetHeight) { index ->
                val x = index % targetWidth
                val y = index / targetWidth
                val sourceX = (x * width / targetWidth).coerceAtMost(width - 1)
                val sourceY = (y * height / targetHeight).coerceAtMost(height - 1)
                sourcePixels[sourceY * width + sourceX]
            }
        }
        val black = BooleanArray(pixels.size)
        pixels.forEachIndexed { index, pixel ->
            val alpha = Color.alpha(pixel)
            val luminance = Color.red(pixel) * 0.299 + Color.green(pixel) * 0.578 + Color.blue(pixel) * 0.114
            black[index] = alpha != 0 && luminance < 128
        }
        return RasterImage(targetWidth, targetHeight, black)
    }

    private fun wrapLine(value: String, paint: Paint, maxWidth: Int): List<String> {
        if (value.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var start = 0
        while (start < value.length) {
            var end = paint.breakText(value, start, value.length, true, maxWidth.toFloat(), null) + start
            if (end <= start) end = start + 1
            result += value.substring(start, end)
            start = end
        }
        return result
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try { block(this) } finally { recycle() }

    private companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        const val MAX_IMAGE_PIXELS = 16_000_000L
        const val MAX_RASTER_TEXT_CHARS = 20_000
        const val MAX_RASTER_HEIGHT = 32_000
    }
}
