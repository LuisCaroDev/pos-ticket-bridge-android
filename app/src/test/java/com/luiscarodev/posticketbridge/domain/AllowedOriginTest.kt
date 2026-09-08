package com.luiscarodev.posticketbridge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllowedOriginTest {
    @Test
    fun normalizesSchemeHostAndDefaultPortLikeDesktop() {
        assertEquals(
            "https://pos.example.com",
            AllowedOrigin.normalizeOrNull(" HTTPS://POS.EXAMPLE.COM:443/ "),
        )
        assertEquals(
            "http://pos.example.com:8080",
            AllowedOrigin.normalizeOrNull("http://POS.EXAMPLE.COM:8080"),
        )
    }

    @Test
    fun rejectsValuesThatAreNotBareHttpOrigins() {
        listOf(
            "pos.example.com",
            "ftp://pos.example.com",
            "https://user:pass@pos.example.com",
            "https://pos.example.com/path",
            "https://pos.example.com/?query=true",
            "https://pos.example.com/#fragment",
            "https://pos.example.com:0",
        ).forEach { assertNull(it, AllowedOrigin.normalizeOrNull(it)) }
    }
}
