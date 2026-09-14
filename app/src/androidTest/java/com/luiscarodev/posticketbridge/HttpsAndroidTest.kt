package com.luiscarodev.posticketbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luiscarodev.posticketbridge.bridge.KtorBridgeHttpServer
import com.luiscarodev.posticketbridge.bridge.https.*
import com.luiscarodev.posticketbridge.contract.PrintJobV1
import com.luiscarodev.posticketbridge.data.BridgeSettings
import com.luiscarodev.posticketbridge.domain.PrinterCatalog
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.printing.BridgePrinterOperations
import java.io.File
import java.net.ServerSocket
import java.net.URL
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpsAndroidTest {
    @Test fun encryptedStoreSurvivesRecreationAndRejectsCorruption() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "https-instrumentation-${System.nanoTime()}"
        val file = File(context.noBackupFilesDir, "$name.enc")
        try {
            val first = AndroidHttpsStore(context, name)
            val material = HttpsCertificates.prepare(null, "127.0.0.1")
            val record = HttpsRecord(enabled = true, selection = HttpsNetwork("lo", "127.0.0.1", 32), material = material)
            first.write(record)
            assertEquals(record, AndroidHttpsStore(context, name).read())
            assertFalse(file.readBytes().decodeToString().contains(material.caKey))
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(runCatching { first.read() }.isFailure)
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        } finally {
            file.delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("pos-ticket-bridge-https-mobile-$name") }
        }
    }

    @Test fun realNettyTlsVerifiesCaHostnameAuthAndCorsOnAndroid() {
        val port = ServerSocket(0).use { it.localPort }
        val material = HttpsCertificates.prepare(null, "127.0.0.1")
        val catalog = object : PrinterCatalog {
            override suspend fun getAll() = emptyList<PrinterDefinition>()
            override suspend fun find(id: String): PrinterDefinition? = null
        }
        val operations = object : BridgePrinterOperations {
            override suspend fun print(printerId: String, job: PrintJobV1) = Unit
            override suspend fun test(printerId: String) = Unit
            override suspend fun openDrawer(printerId: String) = Unit
        }
        val server = KtorBridgeHttpServer(BridgeSettings("secret", listOf("https://pos.example.com"), port), catalog, operations,
            HttpsRecord(enabled = true, selection = HttpsNetwork("lo", "127.0.0.1", 32), material = material))
        fun connection(path: String, host: String = "127.0.0.1", ca: String = material.ca): HttpsURLConnection =
            (URL("https://$host:$port$path").openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = HttpsCertificates.clientContext(ca).socketFactory
                connectTimeout = 3000; readTimeout = 5000
            }
        server.start()
        try {
            repeat(10) {
                val health = connection("/health")
                try {
                    assertEquals(200, health.responseCode)
                    assertTrue(health.inputStream.bufferedReader().use { it.readText() }.contains("https://127.0.0.1:$port"))
                } finally { health.disconnect() }
            }
            val unauthenticated = connection("/test/example")
            try { unauthenticated.requestMethod = "POST"; assertEquals(401, unauthenticated.responseCode) }
            finally { unauthenticated.disconnect() }
            val authenticated = connection("/test/example")
            try {
                authenticated.requestMethod = "POST"
                authenticated.setRequestProperty("x-agent-token", "secret")
                authenticated.setRequestProperty("Origin", "https://pos.example.com")
                assertEquals(200, authenticated.responseCode)
                assertEquals("https://pos.example.com", authenticated.getHeaderField("Access-Control-Allow-Origin"))
            } finally { authenticated.disconnect() }
            val wrongName = connection("/health", "localhost")
            try { assertTrue(runCatching { wrongName.inputStream.use { it.read() } }.isFailure) } finally { wrongName.disconnect() }
            val wrongCa = connection("/health", ca = HttpsCertificates.prepare(null, "127.0.0.1").ca)
            try { assertTrue(runCatching { wrongCa.inputStream.use { it.read() } }.isFailure) } finally { wrongCa.disconnect() }
        } finally { server.stop() }
    }
}
