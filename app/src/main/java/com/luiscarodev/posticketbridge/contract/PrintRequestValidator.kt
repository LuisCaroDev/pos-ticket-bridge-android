package com.luiscarodev.posticketbridge.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface PrintRequestValidation {
    data class Valid(val printerId: String) : PrintRequestValidation
    data object Invalid : PrintRequestValidation
}

object PrintRequestValidator {
    fun validate(body: String): PrintRequestValidation {
        val root = runCatching { BridgeJson.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return PrintRequestValidation.Invalid
        val printerId = root.requiredString("printerId")
            ?.takeIf(String::isNotEmpty)
            ?: return PrintRequestValidation.Invalid
        val job = root["job"] as? JsonObject ?: return PrintRequestValidation.Invalid
        if (!validJob(job)) return PrintRequestValidation.Invalid
        return PrintRequestValidation.Valid(printerId)
    }

    private fun validJob(job: JsonObject): Boolean {
        if (job["version"].numberOrNull() != 1.0) return false
        if (!job.optionalNumberIn("widthMm", setOf(58.0, 80.0))) return false
        if (!job.optionalString("reason") || !job.optionalString("jobId")) return false
        val blocks = job["blocks"] as? JsonArray ?: return false
        return blocks.all { validBlock(it as? JsonObject ?: return@all false) }
    }

    private fun validBlock(block: JsonObject): Boolean = when (block.requiredString("type")) {
        "image" -> block.requiredString("url") != null &&
            block.optionalNumber("maxWidth") && block.optionalNumber("maxHeight")
        "text" -> block.keys.all(TEXT_KEYS::contains) &&
            block.requiredString("content") != null &&
            block.optionalEnum("align", ALIGNMENTS) &&
            block.optionalBoolean("bold") && block.optionalEnum("font", FONTS) &&
            block.optionalScale("width") && block.optionalScale("height") &&
            block.optionalBoolean("underline")
        "table-row" -> block.requiredString("left") != null &&
            block.requiredString("right") != null && block.optionalBoolean("bold") &&
            block.optionalEnum("align", TABLE_ALIGNMENTS)
        "separator" -> block.optionalEnum("style", SEPARATOR_STYLES)
        "feed" -> block.optionalNumber("lines")
        "qr" -> block.requiredString("content") != null && block.optionalScale("size")
        "barcode" -> block.requiredString("content") != null &&
            block.optionalEnum("format", BARCODE_FORMATS)
        "cut" -> block.optionalBoolean("partial")
        "open-drawer" -> true
        else -> false
    }

    private fun JsonObject.requiredString(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.optionalString(key: String): Boolean =
        key !in this || requiredString(key) != null

    private fun JsonObject.optionalBoolean(key: String): Boolean =
        key !in this || (get(key) as? JsonPrimitive)?.booleanOrNull != null

    private fun JsonObject.optionalNumber(key: String): Boolean =
        key !in this || get(key).numberOrNull() != null

    private fun JsonObject.optionalNumberIn(key: String, allowed: Set<Double>): Boolean =
        key !in this || get(key).numberOrNull() in allowed

    private fun JsonObject.optionalEnum(key: String, allowed: Set<String>): Boolean =
        key !in this || requiredString(key) in allowed

    private fun JsonObject.optionalScale(key: String): Boolean {
        if (key !in this) return true
        val value = get(key).numberOrNull() ?: return false
        return value % 1.0 == 0.0 && value in 1.0..8.0
    }

    private fun JsonElement?.numberOrNull(): Double? =
        (this as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.doubleOrNull

    private val ALIGNMENTS = setOf("left", "center", "right")
    private val TABLE_ALIGNMENTS = setOf("left", "right")
    private val FONTS = setOf("standard", "compact", "compact-tall")
    private val SEPARATOR_STYLES = setOf("solid", "dotted")
    private val BARCODE_FORMATS = setOf("CODE128", "EAN13")
    private val TEXT_KEYS = setOf(
        "type", "content", "align", "bold", "font", "width", "height", "underline",
    )
}
