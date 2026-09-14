package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory, cancellable FIFO waiters sharing the full encode/write/close operation. */
class PrintQueue {
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val entries = mutableMapOf<List<Any?>, Entry>()

    suspend fun <T> run(printer: PrinterDefinition, work: suspend () -> T): T {
        val key = destination(printer)
        // Count both active and waiting calls before suspending. Never remove a lock
        // while another caller holds a reference to it (including cancelled waiters).
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also { it.users++ }
        }
        try {
            return entry.mutex.withLock { work() }
        } finally {
            synchronized(entries) {
                if (--entry.users == 0) entries.remove(key)
            }
        }
    }

    private fun destination(printer: PrinterDefinition): List<Any?> = when (printer.tipo) {
        PrinterType.NETWORK -> listOf(printer.tipo,
            printer.host?.trim()?.lowercase(Locale.ROOT), printer.port ?: 9100)
        PrinterType.BLUETOOTH -> listOf(printer.tipo,
            printer.bluetoothAddress?.trim()?.uppercase(Locale.ROOT))
        // A definition without serial can select the same device as one with serial.
        // Conservatively serialize the VID/PID family, as desktop direct USB does.
        PrinterType.USB -> listOf(printer.tipo, printer.usbVendorId, printer.usbProductId)
    }
}
