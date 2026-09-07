package com.luiscarodev.posticketbridge.contract

import com.luiscarodev.posticketbridge.domain.MockPrinter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BRIDGE_VERSION = "1.0.0"

val BridgeJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class HealthResponse(
    val ok: Boolean = true,
    val version: String = BRIDGE_VERSION,
    val suggestedHosts: List<String>,
    val printers: List<MockPrinter>,
)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class BridgeMessage(
    val code: String,
    val params: Map<String, String>? = null,
)

@Serializable
data class ErrorResponse(
    val ok: Boolean = false,
    val error: BridgeMessage,
)

@Serializable
data class TestResponse(
    val ok: Boolean = true,
    val status: String = "success",
    val message: BridgeMessage = BridgeMessage("test_sent"),
)
