package com.luiscarodev.posticketbridge.bridge

import com.luiscarodev.posticketbridge.contract.BridgeJson
import com.luiscarodev.posticketbridge.data.BridgeSettings
import com.luiscarodev.posticketbridge.contract.PrintJobV1
import com.luiscarodev.posticketbridge.domain.PrinterCatalog
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.printing.BridgeOperationException
import com.luiscarodev.posticketbridge.printing.BridgePrinterOperations
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeRoutesTest {
    @Test
    fun privateNetworkPreflightOnlyAllowsConfiguredOrigins() = testApplication {
        application { bridgeModule(settings, catalog, operations) }
        for (origin in listOf("https://pos.example.com", "https://localhost:9977", "https://other.example.com")) {
            val response = client.options("/print") {
                header(HttpHeaders.Origin, origin)
                header("Access-Control-Request-Private-Network", "true")
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(if (origin == "https://other.example.com") null else "true", response.headers["Access-Control-Allow-Private-Network"])
        }
    }
    private val settings = BridgeSettings(
        token = "secret",
        allowedOrigins = listOf("https://pos.example.com"),
    )
    private val configuredPrinters = listOf(
        PrinterDefinition("caja", "Caja", PrinterType.NETWORK, 80, true, true, host = "127.0.0.1", port = 9100),
        PrinterDefinition("cocina", "Cocina", PrinterType.NETWORK, 80, false, true, host = "127.0.0.1", port = 9100),
        PrinterDefinition("usb", "USB", PrinterType.USB, 58, false, true, usbVendorId = 0x04b8, usbProductId = 0x0202),
    )
    private val catalog = object : PrinterCatalog {
        override suspend fun getAll() = configuredPrinters
        override suspend fun find(id: String) = configuredPrinters.firstOrNull { it.id == id }
    }
    private val operations = object : BridgePrinterOperations {
        private suspend fun ensure(id: String) {
            if (catalog.find(id) == null) throw BridgeOperationException("printer_not_found", mapOf("printerId" to id))
        }
        override suspend fun print(printerId: String, job: PrintJobV1) = ensure(printerId)
        override suspend fun test(printerId: String) = ensure(printerId)
        override suspend fun openDrawer(printerId: String) = ensure(printerId)
    }

    @Test
    fun healthIsPublicAndListsStableFakePrinters() = testApplication {
        application { bridgeModule(settings, catalog, operations) }

        val response = client.get("/health")
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.getValue("ok").jsonPrimitive.boolean)
        assertEquals("1.0.0", json.getValue("version").jsonPrimitive.content)
        assertEquals(
            listOf("caja", "cocina", "usb"),
            json.getValue("printers").jsonArray.map {
                it.jsonObject.getValue("id").jsonPrimitive.content
            },
        )
        assertEquals(
            "usb",
            json.getValue("printers").jsonArray.last().jsonObject.getValue("tipo").jsonPrimitive.content,
        )
    }

    @Test
    fun protectedRoutesRejectMissingOrIncorrectTokens() = testApplication {
        application { bridgeModule(settings, catalog, operations) }

        listOf(null, "wrong").forEach { token ->
            val response = client.post("/print") {
                contentType(ContentType.Application.Json)
                token?.let { header("x-agent-token", it) }
                setBody(validPrintBody())
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("invalid_token", errorCode(response.bodyAsText()))
        }
    }

    @Test
    fun printValidatesContractAndSimulatesSuccess() = testApplication {
        application { bridgeModule(settings, catalog, operations) }

        val success = client.post("/print") {
            header("x-agent-token", "secret")
            contentType(ContentType.Application.Json)
            setBody(validPrintBody())
        }
        assertEquals(HttpStatusCode.OK, success.status)
        assertTrue(BridgeJson.parseToJsonElement(success.bodyAsText()).jsonObject["ok"]!!.jsonPrimitive.boolean)

        val invalid = client.post("/print") {
            header("x-agent-token", "secret")
            contentType(ContentType.Application.Json)
            setBody("""{"printerId":"caja","job":{"version":2,"blocks":[]}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("invalid_request", errorCode(invalid.bodyAsText()))
    }

    @Test
    fun unknownPrinterUsesDesktopErrorEnvelope() = testApplication {
        application { bridgeModule(settings, catalog, operations) }

        val response = client.post("/print") {
            header("x-agent-token", "secret")
            contentType(ContentType.Application.Json)
            setBody(validPrintBody(printerId = "missing"))
        }
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertFalse(json.getValue("ok").jsonPrimitive.boolean)
        assertEquals("printer_not_found", json.getValue("error").jsonObject.getValue("code").jsonPrimitive.content)
        assertEquals(
            "missing",
            json.getValue("error").jsonObject.getValue("params").jsonObject
                .getValue("printerId").jsonPrimitive.content,
        )
    }

    @Test
    fun drawerAndTestRoutesSucceedWithoutTouchingHardware() = testApplication {
        application { bridgeModule(settings, catalog, operations) }

        val drawer = client.post("/open-drawer") {
            header("x-agent-token", "secret")
            contentType(ContentType.Application.Json)
            setBody("""{"printerId":"cocina"}""")
        }
        assertEquals(HttpStatusCode.OK, drawer.status)

        val test = client.post("/test/caja") {
            header("x-agent-token", "secret")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val json = BridgeJson.parseToJsonElement(test.bodyAsText()).jsonObject
        assertEquals(HttpStatusCode.OK, test.status)
        assertEquals("success", json.getValue("status").jsonPrimitive.content)
        assertEquals("test_sent", json.getValue("message").jsonObject.getValue("code").jsonPrimitive.content)
    }

    @Test
    fun corsReflectsOnlyConfiguredOriginsAndPreflightNeedsNoToken() = testApplication {
        application { bridgeModule(settings, catalog, operations) }

        val allowed = client.options("/print") {
            header(HttpHeaders.Origin, "https://pos.example.com")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals(HttpStatusCode.NoContent, allowed.status)
        assertEquals(
            "https://pos.example.com",
            allowed.headers[HttpHeaders.AccessControlAllowOrigin],
        )

        val denied = client.options("/print") {
            header(HttpHeaders.Origin, "https://other.example.com")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals(HttpStatusCode.NoContent, denied.status)
        assertNull(denied.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    private fun validPrintBody(printerId: String = "caja") =
        """{"printerId":"$printerId","job":{"version":1,"widthMm":80,"blocks":[{"type":"text","content":"Ticket"}]}}"""

    private fun errorCode(body: String): String {
        val json: JsonObject = BridgeJson.parseToJsonElement(body).jsonObject
        return json.getValue("error").jsonObject.getValue("code").jsonPrimitive.content
    }
}
