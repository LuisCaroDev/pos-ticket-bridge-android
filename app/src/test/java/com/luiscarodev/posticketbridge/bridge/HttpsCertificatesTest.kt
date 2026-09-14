package com.luiscarodev.posticketbridge.bridge

import com.luiscarodev.posticketbridge.bridge.https.*
import org.junit.Assert.*
import org.junit.Test

class HttpsCertificatesTest {
    @Test fun caIsUniqueMobileAndSurvivesIpChangesAndRenewal() {
        val now = System.currentTimeMillis()
        val first = HttpsCertificates.prepare(null, "192.168.1.10", now)
        val ca = HttpsCertificates.certificate(first.ca)
        assertTrue(ca.subjectX500Principal.name.startsWith("CN=POS Ticket Bridge "))
        assertTrue(ca.subjectX500Principal.name.endsWith(" mobile"))
        assertEquals(0, ca.basicConstraints)
        assertNotEquals(first.ca, HttpsCertificates.prepare(null, "192.168.1.10", now).ca)
        assertEquals(first, HttpsCertificates.prepare(first, "192.168.1.10", now + 1000))
        val changed = HttpsCertificates.prepare(first, "192.168.1.11", now)
        assertEquals(first.ca, changed.ca)
        assertNotEquals(first.certificate, changed.certificate)
        val renewed = HttpsCertificates.prepare(first, "192.168.1.10", now + 340L * 86_400_000)
        assertEquals(first.ca, renewed.ca)
        assertNotEquals(first.certificate, renewed.certificate)
        HttpsCertificates.certificate(changed.certificate).verify(ca.publicKey)
    }

    @Test fun wrongCaKeyIsRejectedAndPublicProfileContainsNoPrivateKey() {
        val first = HttpsCertificates.prepare(null, "192.168.1.10")
        val other = HttpsCertificates.prepare(null, "192.168.1.10")
        assertTrue(runCatching { HttpsCertificates.prepare(first.copy(caKey = other.caKey), "192.168.1.10") }.isFailure)
        val profile = HttpsCertificates.iosProfile(first.ca).decodeToString()
        assertTrue(profile.contains("POS Ticket Bridge mobile"))
        assertTrue(profile.contains(first.ca))
        assertFalse(profile.contains(first.caKey))
        assertFalse(profile.contains(first.key))
    }

    @Test fun enrollmentAllowsOnlySelectedSubnet() {
        val network = HttpsNetwork("wlan0", "192.168.1.10", 24)
        assertTrue(sameSubnet("192.168.1.42", network))
        assertTrue(sameSubnet("::ffff:192.168.1.42", network))
        assertFalse(sameSubnet("192.168.2.42", network))
        assertFalse(sameSubnet("127.0.0.1", network))
        assertFalse(sameSubnet("192.168.1.999", network))
        assertFalse(sameSubnet("::1", network))
    }

    @Test fun controllerRollsBackAndRecoversNetworkWithoutRotatingCa() {
        val network = HttpsNetwork("wlan0", "192.168.1.10", 24)
        var available = listOf(network)
        var persisted = HttpsRecord()
        var reject = false
        val store = object : HttpsStore {
            override fun read() = persisted
            override fun write(record: HttpsRecord) { persisted = record }
        }
        val repo = HttpsRepository()
        val controller = LocalHttpsController(store, repo, { record -> object : BridgeHttpServer {
            override fun start() { check(!reject || !record.enabled) { "port_in_use" } }
            override fun stop() = Unit
        } }, 9977, { available })
        try {
            controller.restart()
            reject = true
            assertTrue(runCatching { controller.execute(HttpsAction.Apply(true, network)) }.isFailure)
            assertEquals("http", repo.state.value.transport)
            assertFalse(persisted.enabled)
            reject = false
            controller.execute(HttpsAction.Apply(true, network))
            val ca = persisted.material!!.ca
            available = emptyList()
            assertTrue(runCatching { controller.reconcile() }.isFailure)
            assertEquals("stopped", repo.state.value.transport)
            available = listOf(network.copy(address = "192.168.1.11"))
            controller.reconcile()
            assertEquals("https://192.168.1.11:9977", repo.state.value.host)
            assertEquals(ca, persisted.material!!.ca)
            controller.execute(HttpsAction.Apply(false, available.single()))
            assertEquals(ca, persisted.material!!.ca)
            controller.execute(HttpsAction.Reset)
            assertNull(persisted.material)
        } finally { controller.shutdown() }
    }
}
