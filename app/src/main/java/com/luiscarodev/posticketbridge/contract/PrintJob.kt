package com.luiscarodev.posticketbridge.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrintRequest(val printerId: String, val job: PrintJobV1)

@Serializable
data class PrintJobV1(
    val version: Int,
    val widthMm: Int? = null,
    val reason: String? = null,
    val jobId: String? = null,
    val blocks: List<PrintBlock>,
)

@Serializable
sealed interface PrintBlock

@Serializable
@SerialName("image")
data class ImageBlock(val url: String, val maxWidth: Double? = null, val maxHeight: Double? = null) : PrintBlock

@Serializable
@SerialName("text")
data class TextBlock(
    val content: String,
    val align: String? = null,
    val bold: Boolean = false,
    val font: String? = null,
    val width: Int = 1,
    val height: Int = 1,
    val underline: Boolean = false,
) : PrintBlock

@Serializable
@SerialName("table-row")
data class TableRowBlock(
    val left: String,
    val right: String,
    val bold: Boolean = false,
    val align: String? = null,
) : PrintBlock

@Serializable @SerialName("separator")
data class SeparatorBlock(val style: String? = null) : PrintBlock

@Serializable @SerialName("feed")
data class FeedBlock(val lines: Double? = null) : PrintBlock

@Serializable @SerialName("qr")
data class QrBlock(val content: String, val size: Int = 4) : PrintBlock

@Serializable @SerialName("barcode")
data class BarcodeBlock(val content: String, val format: String = "CODE128") : PrintBlock

@Serializable @SerialName("cut")
data class CutBlock(val partial: Boolean = false) : PrintBlock

@Serializable @SerialName("open-drawer")
data object OpenDrawerBlock : PrintBlock

