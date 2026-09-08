package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.contract.OpenDrawerBlock
import com.luiscarodev.posticketbridge.contract.PrintJobV1
import com.luiscarodev.posticketbridge.domain.PrinterCatalog
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BridgeOperationException(
    val code: String,
    val params: Map<String, String>? = null,
    cause: Throwable? = null,
) : Exception(code, cause)

interface BridgePrinterOperations {
    suspend fun print(printerId: String, job: PrintJobV1)
    suspend fun test(printerId: String)
    suspend fun openDrawer(printerId: String)
}

class PrintCoordinator(
    private val printers: PrinterCatalog,
    private val encoder: EscPosEncoder,
    private val transports: PrinterTransportFactory,
) : BridgePrinterOperations {
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun print(printerId: String, job: PrintJobV1) = withPrinter(printerId) { printer ->
        val bytes = try { encoder.encode(job, printer) }
        catch (error: Throwable) { throw BridgeOperationException(error.message ?: "print_encode_failed", cause = error) }
        send(printer, bytes)
    }

    override suspend fun test(printerId: String) = withPrinter(printerId) { printer ->
        sendTest(printer)
    }

    suspend fun testConfiguration(printer: PrinterDefinition) {
        val key = printer.id.ifBlank { "draft:${printer.tipo}:${printer.nombre}" }
        locks.getOrPut(key) { Mutex() }.withLock { sendTest(printer) }
    }

    private suspend fun sendTest(printer: PrinterDefinition) {
        send(printer, encoder.encode(mobileTestPrintJob(printer), printer))
    }

    override suspend fun openDrawer(printerId: String) = withPrinter(printerId) { printer ->
        if (!printer.abreCajon) return@withPrinter
        val job = PrintJobV1(1, printer.anchoMm, "open-drawer", blocks = listOf(OpenDrawerBlock))
        send(printer, encoder.encode(job, printer))
    }

    private suspend fun <T> withPrinter(id: String, action: suspend (PrinterDefinition) -> T): T {
        val printer = printers.find(id)
            ?: throw BridgeOperationException("printer_not_found", mapOf("printerId" to id))
        if (!printer.enabled) throw BridgeOperationException("printer_disabled", mapOf("printerId" to id))
        return locks.getOrPut(id) { Mutex() }.withLock { action(printer) }
    }

    private suspend fun send(printer: PrinterDefinition, bytes: ByteArray) {
        try { transports.create(printer).write(bytes) }
        catch (error: BridgeOperationException) { throw error }
        catch (error: SecurityException) {
            throw BridgeOperationException(
                permissionErrorCode(printer.tipo),
                mapOf("printerId" to printer.id),
                error,
            )
        }
        catch (error: Throwable) {
            val code = when (error.message) {
                "bluetooth_permission_required", "bluetooth_disabled",
                "usb_permission_required", "usb_disconnected", "usb_ambiguous_device",
                "usb_incompatible", "usb_open_failed", "usb_claim_failed", "usb_write_failed" -> error.message!!
                else -> "printer_unreachable"
            }
            throw BridgeOperationException(code, mapOf("printerId" to printer.id), error)
        }
    }
}

internal fun permissionErrorCode(type: PrinterType): String = when (type) {
    PrinterType.NETWORK -> "local_network_permission_required"
    PrinterType.BLUETOOTH -> "bluetooth_permission_required"
    PrinterType.USB -> "usb_permission_required"
}
