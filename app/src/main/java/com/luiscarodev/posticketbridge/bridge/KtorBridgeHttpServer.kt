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
import com.luiscarodev.posticketbridge.domain.PrinterCatalog
import com.luiscarodev.posticketbridge.domain.summary
import com.luiscarodev.posticketbridge.printing.BridgeOperationException
import com.luiscarodev.posticketbridge.printing.BridgePrinterOperations
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.engine.connector
import io.ktor.server.engine.sslConnector
import com.luiscarodev.posticketbridge.bridge.https.HttpsRecord
import com.luiscarodev.posticketbridge.bridge.https.HttpsCertificates
import io.ktor.server.engine.EmbeddedServer
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

class KtorBridgeHttpServer(
    private val settings: BridgeSettings,
    private val printers: PrinterCatalog,
    private val operations: BridgePrinterOperations,
    private val https: HttpsRecord = HttpsRecord(),
) : BridgeHttpServer {
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    override fun start() {
        check(engine == null) { "Bridge HTTP server is already running" }
        // Use Android's JSSE implementation; desktop tcnative/OpenSSL is not packaged.
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true")
        val next = embeddedServer(Netty, configure = {
            enableHttp2 = false
            enableH2c = false
            connectionGroupSize = 1
            workerGroupSize = 2
            callGroupSize = 2
            requestReadTimeoutSeconds = 15
            responseWriteTimeoutSeconds = 30
            if (https.enabled) {
                sslConnector(HttpsCertificates.keyStore(requireNotNull(https.material)), "bridge", { CharArray(0) }, { CharArray(0) }) {
                    host = requireNotNull(https.selection).address
                    port = settings.port
                    enabledProtocols = javax.net.ssl.SSLContext.getDefault().supportedSSLParameters.protocols
                        .filter { it == "TLSv1.2" || it == "TLSv1.3" }
                }
            } else connector { host = "0.0.0.0"; port = settings.port }
        }) {
            bridgeModule(settings, printers, operations,
                if (https.enabled) listOf("https://${https.selection!!.address}:${settings.port}") else null)
        }
        engine = next
        try { next.start(wait = false) } catch (error: Exception) { stop(); throw error }
    }

    override fun stop() {
        engine?.stop(gracePeriodMillis = 1_000, timeoutMillis = 3_000)
        engine = null
    }
}

fun Application.bridgeModule(
    settings: BridgeSettings,
    printers: PrinterCatalog,
    operations: BridgePrinterOperations,
    advertisedHosts: List<String>? = null,
) {
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
            if ((origin == null || isAllowedOrigin(origin, settings)) &&
                call.request.header("access-control-request-private-network") == "true") {
                call.response.headers.append("Access-Control-Allow-Private-Network", "true")
            }
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
                    suggestedHosts = advertisedHosts ?: suggestedHosts(settings.port),
                    printers = printers.getAll().map { it.summary() },
                ),
            )
        }

        post("/print") {
            val body = call.receiveBodyOrNull()
            val validation = body?.let(PrintRequestValidator::validate)
            when (validation) {
                is PrintRequestValidation.Valid -> call.respondOperation {
                    operations.print(validation.printerId, validation.request.job)
                    call.respond(OkResponse())
                }
                else -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = BridgeMessage("invalid_request")),
                )
            }
        }

        post("/open-drawer") {
            val printerId = call.receiveBodyOrNull()?.let(::printerIdFromActionBody)
            if (printerId == null) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = BridgeMessage("printer_not_found", mapOf("printerId" to ""))))
            } else call.respondOperation {
                operations.openDrawer(printerId)
                call.respond(OkResponse())
            }
        }

        post("/test/{printerId}") {
            val printerId = call.parameters["printerId"].orEmpty()
            call.respondOperation {
                operations.test(printerId)
                call.respond(TestResponse())
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondOperation(
    operation: suspend () -> Unit,
) = try {
    operation()
} catch (error: BridgeOperationException) {
    respond(HttpStatusCode.InternalServerError, ErrorResponse(error = BridgeMessage(error.code, error.params)))
} catch (_: Throwable) {
    respond(HttpStatusCode.InternalServerError, ErrorResponse(error = BridgeMessage("printer_unreachable")))
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
        origin == "https://localhost:${settings.port}" ||
        origin == "https://127.0.0.1:${settings.port}" ||
        origin in settings.allowedOrigins

private fun tokenMatches(expected: String, actual: String?): Boolean {
    if (actual == null) return false
    return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
}
