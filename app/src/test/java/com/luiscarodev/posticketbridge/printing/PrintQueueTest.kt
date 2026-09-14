package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PrintQueueTest {
    private val printer = PrinterDefinition("one", "Caja", PrinterType.NETWORK, 80,
        true, true, host = "PRINTER.local", port = null)

    @Test fun aliasesWaitInOrderAndOtherDestinationsRunIndependently() = runBlocking {
        withTimeout(5_000) {
            val queue = PrintQueue()
            val gate = CompletableDeferred<Unit>()
            val events = mutableListOf<Int>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                queue.run(printer) { events += 0; gate.await() }
            }
            val waiting = (1..5).map { index ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    queue.run(printer.copy(id = "$index", host = "printer.local", port = 9100)) {
                        events += index
                    }
                }
            }
            queue.run(printer.copy(host = "other.local")) { events += 10 }
            queue.run(printer.copy(port = 9200)) { events += 11 }
            assertEquals(listOf(0, 10, 11), events)
            gate.complete(Unit)
            first.join(); waiting.joinAll()
            assertEquals(listOf(0, 10, 11, 1, 2, 3, 4, 5), events)
        }
    }

    @Test fun cancellationAndFailureDoNotPoisonTheQueue() = runBlocking {
        withTimeout(5_000) {
            val queue = PrintQueue()
            val gate = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    queue.run(printer) { gate.await(); error("failed") }
                } catch (_: IllegalStateException) { events += "failed" }
            }
            val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
                queue.run(printer) { fail("Cancelled waiter wrote to printer") }
            }
            val next = launch(start = CoroutineStart.UNDISPATCHED) {
                queue.run(printer) { events += "next" }
            }
            cancelled.cancelAndJoin()
            assertTrue(events.isEmpty())
            gate.complete(Unit)
            first.join(); next.join()
            assertEquals(listOf("failed", "next"), events)
            queue.run(printer) { events += "reused" }
            assertEquals("reused", events.last())
        }
    }

    @Test fun activeCancellationKeepsLockUntilTransportCleanupFinishes() = runBlocking {
        withTimeout(5_000) {
            val queue = PrintQueue()
            val cleanup = CompletableDeferred<Unit>()
            var started = false
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                queue.run(printer) {
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { cleanup.await() } }
                }
            }
            first.cancel()
            val next = launch(start = CoroutineStart.UNDISPATCHED) {
                queue.run(printer) { started = true }
            }
            yield()
            assertFalse(started)
            cleanup.complete(Unit)
            first.join(); next.join()
            assertTrue(started)
        }
    }

    @Test fun bluetoothAndUsbAliasesShareTheirDestination() = runBlocking {
        withTimeout(5_000) {
            val bluetooth = printer.copy(tipo = PrinterType.BLUETOOTH, bluetoothAddress = "aa:bb:cc:dd:ee:ff")
            val usb = printer.copy(tipo = PrinterType.USB, usbVendorId = 123, usbProductId = 456)
            for ((saved, alias) in listOf(
                bluetooth to bluetooth.copy(id = "", bluetoothAddress = "AA:BB:CC:DD:EE:FF"),
                usb to usb.copy(id = "other", usbSerialNumber = "serial"),
            )) {
                val queue = PrintQueue()
                val gate = CompletableDeferred<Unit>()
                var started = false
                val first = launch(start = CoroutineStart.UNDISPATCHED) { queue.run(saved) { gate.await() } }
                val next = launch(start = CoroutineStart.UNDISPATCHED) { queue.run(alias) { started = true } }
                assertFalse(started)
                gate.complete(Unit)
                first.join(); next.join()
                assertTrue(started)
            }
        }
    }

    @Test fun concurrentAdmissionAndRemovalNeverCreateTwoActiveWriters() = runBlocking {
        withTimeout(10_000) {
            val queue = PrintQueue()
            val active = AtomicInteger()
            val completed = AtomicInteger()
            List(500) { index ->
                launch(Dispatchers.Default) {
                    queue.run(printer.copy(id = "$index")) {
                        assertEquals(1, active.incrementAndGet())
                        try { yield(); completed.incrementAndGet() }
                        finally { active.decrementAndGet() }
                    }
                }
            }.joinAll()
            assertEquals(500, completed.get())
        }
    }
}
