package com.luiscarodev.posticketbridge.bridge

import com.luiscarodev.posticketbridge.contract.BridgeJson
import com.luiscarodev.posticketbridge.contract.BridgeMessage
import com.luiscarodev.posticketbridge.contract.ErrorResponse
import com.luiscarodev.posticketbridge.contract.HealthResponse
import com.luiscarodev.posticketbridge.contract.OkResponse
import com.luiscarodev.posticketbridge.contract.PrintRequestValidation
import com.luiscarodev.posticketbridge.contract.PrintRequestValidator
import com.luiscarodev.posticketbridge.contract.TestResponse
import com.luiscarodev.posticketbridge.data.BridgeSettings
import com.luiscarodev.posticketbridge.domain.MockPrinterCatalog
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.security.MessageDigest

private const val MAX_REQUEST_CHARS = 1_000_000

class KtorBridgeHttpServer(private val settings: BridgeSettings) : BridgeHttpServer {
    private var engine: ApplicationEngine? = null

    override fun start() {
        check(engine == null) { "Bridge HTTP server is already running" }
        engine = embeddedServer(CIO, host = "0.0.0.0", port = settings.port) {
            bridgeModule(settings)
        }.also { it.start(wait = false) }
    }

    override fun stop() {
        engine?.stop(gracePeriodMillis = 1_000, timeoutMillis = 3_000)
        engine = null
    }
}

fun Application.bridgeModule(settings: BridgeSettings) {
    install(ContentNegotiation) { json(BridgeJson) }

    intercept(ApplicationCallPipeline.Plugins) {
        val origin = call.request.header(HttpHeaders.Origin)
        if (origin != null && isAllowedOrigin(origin, settings)) {
            call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, origin)
            call.response.headers.append(HttpHeaders.Vary, HttpHeaders.Origin)
            call.response.headers.append(
                HttpHeaders.AccessControlAllowMethods,
                "GET,POST,PUT,DELETE,OPTIONS",
            )
            call.response.headers.append(
                HttpHeaders.AccessControlAllowHeaders,
                "content-type,x-agent-token",
            )
        }

        if (call.request.httpMethod == HttpMethod.Options) {
            call.respond(HttpStatusCode.NoContent)
            finish()
            return@intercept
        }

        if (call.request.path() != "/health" && !tokenMatches(
                expected = settings.token,
                actual = call.request.header("x-agent-token"),
            )
        ) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(error = BridgeMessage("invalid_token")),
            )
            finish()
        }
    }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    suggestedHosts = suggestedHosts(settings.port),
                    printers = MockPrinterCatalog.printers,
                ),
            )
        }

        post("/print") {
            val body = call.receiveBodyOrNull()
            val validation = body?.let(PrintRequestValidator::validate)
            when (validation) {
                is PrintRequestValidation.Valid -> respondMockOperation(validation.printerId)
                else -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = BridgeMessage("invalid_request")),
                )
            }
        }

        post("/open-drawer") {
            val printerId = call.receiveBodyOrNull()?.let(::printerIdFromActionBody)
            respondMockOperation(printerId.orEmpty())
        }

        post("/test/{printerId}") {
            val printerId = call.parameters["printerId"].orEmpty()
            if (MockPrinterCatalog.contains(printerId)) {
                call.respond(TestResponse())
            } else {
                call.respondPrinterNotFound(printerId)
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondMockOperation(printerId: String) {
    if (MockPrinterCatalog.contains(printerId)) {
        respond(OkResponse())
    } else {
        respondPrinterNotFound(printerId)
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondPrinterNotFound(
    printerId: String,
) {
    respond(
        HttpStatusCode.InternalServerError,
        ErrorResponse(
            error = BridgeMessage(
                code = "printer_not_found",
                params = mapOf("printerId" to printerId),
            ),
        ),
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.receiveBodyOrNull(): String? =
    runCatching { receiveText() }.getOrNull()?.takeIf { it.length <= MAX_REQUEST_CHARS }

private fun printerIdFromActionBody(body: String): String? {
    val root = runCatching { BridgeJson.parseToJsonElement(body) }.getOrNull()
        as? kotlinx.serialization.json.JsonObject ?: return null
    val value = root["printerId"] as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return value.takeIf { it.isString }?.content
}

private fun isAllowedOrigin(origin: String, settings: BridgeSettings): Boolean =
    origin == "http://localhost:${settings.port}" ||
        origin == "http://127.0.0.1:${settings.port}" ||
        origin in settings.allowedOrigins

private fun tokenMatches(expected: String, actual: String?): Boolean {
    if (actual == null) return false
    return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
}
