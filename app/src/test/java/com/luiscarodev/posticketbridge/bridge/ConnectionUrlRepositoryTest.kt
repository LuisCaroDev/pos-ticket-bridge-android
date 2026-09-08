package com.luiscarodev.posticketbridge.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionUrlRepositoryTest {
    @Test
    fun usesLocalhostOnlyWhenNoSharedNetworkExists() {
        val urls = prioritizeConnectionUrls(emptyList(), 9977)

        assertEquals(ConnectionKind.LOCAL, urls.primary.kind)
        assertEquals("http://127.0.0.1:9977", urls.primary.value)
        assertEquals(emptyList<ConnectionUrl>(), urls.alternatives)
    }

    @Test
    fun prioritizesActiveSharedNetworkAndKeepsCopyableAlternatives() {
        val urls = prioritizeConnectionUrls(
            listOf(
                AddressCandidate("192.168.43.1", ConnectionKind.HOTSPOT, active = false),
                AddressCandidate("192.168.18.68", ConnectionKind.WIFI, active = true),
                AddressCandidate("192.168.18.68", ConnectionKind.HOTSPOT, active = false),
            ),
            9977,
        )

        assertEquals(
            ConnectionUrl("http://192.168.18.68:9977", ConnectionKind.WIFI),
            urls.primary,
        )
        assertEquals(
            listOf(
                ConnectionUrl("http://192.168.43.1:9977", ConnectionKind.HOTSPOT),
                ConnectionUrl("http://127.0.0.1:9977", ConnectionKind.LOCAL),
            ),
            urls.alternatives,
        )
    }
}
