package com.luiscarodev.posticketbridge.bridge

import com.luiscarodev.posticketbridge.bridge.https.*
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class HttpsEnrollmentTest {
    @Test fun servesOnlyPublicCertificatesAndRejectsExpiredSessions() {
        val port = ServerSocket(0).use { it.localPort }
        val network = HttpsNetwork("test", "127.0.0.1", 8)
        val ca = HttpsCertificates.prepare(null, "127.0.0.1").ca
        val server = HttpsEnrollmentServer(port)
        fun request(path: String, method: String = "GET", verify: (HttpURLConnection) -> Unit) {
            val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            try { connection.requestMethod = method; connection.connectTimeout = 2000; connection.readTimeout = 2000; verify(connection) }
            finally { connection.disconnect() }
        }
        try {
            server.start(network, ca, System.currentTimeMillis() + 60_000)
            request("/setup/android.cer") {
                assertEquals(200, it.responseCode)
                assertEquals("no-store", it.getHeaderField("Cache-Control"))
                assertArrayEquals(Base64.getDecoder().decode(ca), it.inputStream.use { stream -> stream.readBytes() })
            }
            request("/setup/android.cer", "HEAD") {
                assertEquals(200, it.responseCode)
                assertEquals(Base64.getDecoder().decode(ca).size, it.contentLength)
                assertEquals(-1, it.inputStream.read())
            }
            request("/health") { assertEquals(404, it.responseCode) }
            request("/setup/android.cer", "POST") { assertEquals(405, it.responseCode) }
            server.start(network, ca, System.currentTimeMillis() - 1)
            request("/setup/android.cer") { assertEquals(403, it.responseCode) }
        } finally { server.stop() }
        assertTrue(runCatching { request("/setup/android.cer") { it.inputStream.read() } }.isFailure)
    }
}
