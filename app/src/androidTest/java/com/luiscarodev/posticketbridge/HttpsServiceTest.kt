package com.luiscarodev.posticketbridge

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.luiscarodev.posticketbridge.bridge.BridgeForegroundService
import com.luiscarodev.posticketbridge.bridge.https.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class HttpsServiceTest {
    @Test fun activationEnrollmentPauseAndRestartPreserveCa() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as BridgeApplication
        assumeTrue("Uses a fresh development installation", AndroidHttpsStore(context).read() == HttpsRecord())
        val networks = localHttpsNetworks()
        assumeTrue("Requires Wi-Fi or Ethernet", networks.isNotEmpty())
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 37) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_LOCAL_NETWORK)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val repo = app.httpsRepository
            withTimeout(10_000) { repo.state.first { it.loaded && it.transport != "stopped" } }
            try {
                withTimeout(10_000) { repo.execute(HttpsAction.Apply(true, networks.first())) }
                assertEquals("https", repo.state.value.transport)
                val fingerprint = repo.state.value.fingerprint
                val health = URL("${repo.state.value.host}/health").openConnection() as HttpsURLConnection
                try {
                    health.sslSocketFactory = HttpsCertificates.clientContext(requireNotNull(repo.clientCa)).socketFactory
                    health.connectTimeout = 5000; health.readTimeout = 5000
                    assertEquals(200, health.responseCode)
                } finally { health.disconnect() }
                withTimeout(10_000) { repo.execute(HttpsAction.Enroll(ClientOs.ANDROID)) }
                val session = requireNotNull(repo.state.value.enrollment)
                // Simulate the browser downloader without changing the app's outbound
                // cleartext policy. Only the separate public-certificate listener is used.
                val url = URL(session.url)
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(url.host, url.port), 5000)
                    socket.soTimeout = 5000
                    socket.getOutputStream().write("GET ${url.path} HTTP/1.1\r\nHost: ${url.host}:${url.port}\r\nConnection: close\r\n\r\n".toByteArray())
                    val response = socket.getInputStream().readBytes()
                    val text = response.toString(Charsets.ISO_8859_1)
                    assertTrue(text.startsWith("HTTP/1.1 200"))
                    val bytes = response.copyOfRange(text.indexOf("\r\n\r\n") + 4, response.size)
                    assertEquals(fingerprint, HttpsCertificates.fingerprint(java.util.Base64.getEncoder().encodeToString(bytes)))
                }
                withTimeout(10_000) { repo.execute(HttpsAction.StopEnrollment) }
                assertNull(repo.state.value.enrollment)
                scenario.recreate()
                withTimeout(10_000) { repo.execute(HttpsAction.Retry) }
                assertEquals(fingerprint, repo.state.value.fingerprint)
                withTimeout(10_000) { repo.execute(HttpsAction.Apply(false, networks.first())) }
                assertEquals("http", repo.state.value.transport)
                assertEquals(fingerprint, repo.state.value.fingerprint)
            } finally {
                withTimeout(10_000) { repo.execute(HttpsAction.Reset) }
                BridgeForegroundService.stop(context)
            }
        }
    }
}
