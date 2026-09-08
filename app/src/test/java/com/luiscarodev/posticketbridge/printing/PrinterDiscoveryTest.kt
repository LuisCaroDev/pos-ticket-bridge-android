package com.luiscarodev.posticketbridge.printing

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterDiscoveryTest {
    @Test
    fun buildsOnlyTheSelectedSlash24() {
        val hosts = hostsIn24("192.168.7.42")
        assertEquals(254, hosts.size)
        assertEquals("192.168.7.1", hosts.first())
        assertEquals("192.168.7.254", hosts.last())
    }

    @Test
    fun scanExcludesOwnAddressKeepsOrderAndCapsConcurrency() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val calls = AtomicInteger()
        val scanner = NetworkPrinterScanner(PortProbe { host, port, timeout ->
            assertEquals(9100, port)
            assertEquals(500, timeout)
            calls.incrementAndGet()
            val now = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, now) }
            delay(1)
            active.decrementAndGet()
            host.endsWith(".2") || host.endsWith(".200")
        })

        val results = scanner.scan("192.168.7.42")

        assertEquals(253, calls.get())
        assertTrue(maximum.get() <= 32)
        assertEquals(listOf("192.168.7.2", "192.168.7.200"), results.map { it.host })
    }

    @Test
    fun scanCanBeCancelled() = runBlocking {
        val active = AtomicInteger()
        val scanner = NetworkPrinterScanner(PortProbe { _, _, _ ->
            active.incrementAndGet()
            try { delay(30_000); false } finally { active.decrementAndGet() }
        })
        val job = launch { scanner.scan("10.0.0.4") }
        while (active.get() == 0) yield()
        job.cancelAndJoin()
        assertEquals(0, active.get())
    }
}
