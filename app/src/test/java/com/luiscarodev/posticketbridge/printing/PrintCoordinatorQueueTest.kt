package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.contract.PrintJobV1
import com.luiscarodev.posticketbridge.contract.TextBlock
import com.luiscarodev.posticketbridge.domain.PrinterCatalog
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PrintCoordinatorQueueTest {
    private val saved = PrinterDefinition("saved", "Caja", PrinterType.NETWORK, 80,
        true, true, profileId = "epson-escpos-usb", host = "printer.local")
    private val alias = saved.copy(id = "alias")
    private val catalog = object : PrinterCatalog {
        override suspend fun getAll() = listOf(saved, alias)
        override suspend fun find(id: String) = getAll().find { it.id == id }
    }
    private val encoder = EscPosEncoder(object : PrintRasterizer {
        override suspend fun image(source: String, maxWidth: Int, maxHeight: Int?) =
            RasterImage(1, 1, booleanArrayOf(true))
        override fun text(block: TextBlock, maxWidth: Int) = RasterImage(1, 1, booleanArrayOf(true))
    })
    private val job = PrintJobV1(1, blocks = listOf(TextBlock("ticket")))

    @Test fun printDrawerSavedTestAndDraftShareOneQueue() = runBlocking {
        withTimeout(5_000) {
            val gate = CompletableDeferred<Unit>()
            val writes = mutableListOf<String>()
            val coordinator = PrintCoordinator(catalog, encoder) { printer ->
                PrinterTransport {
                    writes += printer.id
                    if (writes.size == 1) gate.await()
                }
            }
            val first = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.print(saved.id, job) }
            val drawer = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.openDrawer(alias.id) }
            val test = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.test(saved.id) }
            val draft = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.testConfiguration(saved.copy(id = ""))
            }
            assertEquals(listOf("saved"), writes)
            assertFalse(drawer.isCompleted)
            gate.complete(Unit)
            listOf(first, drawer, test, draft).joinAll()
            assertEquals(listOf("saved", "alias", "saved", ""), writes)
        }
    }

    @Test fun transportCancellationIsPreservedAndNextPrintCanRun() = runBlocking {
        var calls = 0
        val coordinator = PrintCoordinator(catalog, encoder) {
            PrinterTransport { if (++calls == 1) throw CancellationException("cancelled") }
        }
        try {
            coordinator.print(saved.id, job)
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
        coordinator.print(alias.id, job)
        assertEquals(2, calls)
    }
}
