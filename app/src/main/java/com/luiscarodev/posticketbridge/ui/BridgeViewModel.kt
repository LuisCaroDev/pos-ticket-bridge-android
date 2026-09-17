package com.luiscarodev.posticketbridge.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luiscarodev.posticketbridge.BridgeApplication
import com.luiscarodev.posticketbridge.BuildConfig
import com.luiscarodev.posticketbridge.bridge.BridgeForegroundService
import com.luiscarodev.posticketbridge.bridge.BridgeRuntimeState
import com.luiscarodev.posticketbridge.bridge.ConnectionUrl
import com.luiscarodev.posticketbridge.bridge.ConnectionUrls
import com.luiscarodev.posticketbridge.bridge.ConnectionKind
import com.luiscarodev.posticketbridge.domain.PairedBluetoothPrinter
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.NetworkPrinterCandidate
import com.luiscarodev.posticketbridge.domain.UsbPrinterCandidate
import com.luiscarodev.posticketbridge.domain.PrinterProfileCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BridgeUiState(
    val runtime: BridgeRuntimeState = BridgeRuntimeState.Stopped,
    val token: String = "",
    val allowedOrigins: List<String> = emptyList(),
    val port: Int = BuildConfig.DEFAULT_BRIDGE_PORT,
    val saving: Boolean = false,
    val resultMessage: String? = null,
    val portSaving: Boolean = false,
    val portResultMessage: String? = null,
    val portResultIsError: Boolean = false,
    val connectionUrls: ConnectionUrls = ConnectionUrls(
        primary = ConnectionUrl("http://127.0.0.1:${BuildConfig.DEFAULT_BRIDGE_PORT}", ConnectionKind.LOCAL),
        alternatives = emptyList(),
    ),
    val printers: List<PrinterDefinition> = emptyList(),
    val pairedBluetooth: List<PairedBluetoothPrinter> = emptyList(),
    val networkCandidates: List<NetworkPrinterCandidate> = emptyList(),
    val usbCandidates: List<UsbPrinterCandidate> = emptyList(),
    val discoveryBusy: Boolean = false,
    val discoveryError: String? = null,
    val printerBusy: Boolean = false,
    val printerFormFeedback: PrinterFormFeedback? = null,
)

data class PrinterFormFeedback(val message: String, val isError: Boolean)

private data class PrinterUiParts(
    val saving: Boolean,
    val message: String?,
    val printers: List<PrinterDefinition>,
    val paired: List<PairedBluetoothPrinter>,
    val busy: Boolean,
    val networkCandidates: List<NetworkPrinterCandidate>,
    val usbCandidates: List<UsbPrinterCandidate>,
    val discoveryBusy: Boolean,
    val discoveryError: String?,
    val formFeedback: PrinterFormFeedback?,
)

private data class PortFeedback(val saving: Boolean, val message: String?, val isError: Boolean)

class BridgeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as BridgeApplication
    private val saving = MutableStateFlow(false)
    private val resultMessage = MutableStateFlow<String?>(null)
    private val portSaving = MutableStateFlow(false)
    private val portResultMessage = MutableStateFlow<String?>(null)
    private val portResultIsError = MutableStateFlow(false)
    private val pairedBluetooth = MutableStateFlow<List<PairedBluetoothPrinter>>(emptyList())
    private val printerBusy = MutableStateFlow(false)
    private val networkCandidates = MutableStateFlow<List<NetworkPrinterCandidate>>(emptyList())
    private val usbCandidates = MutableStateFlow<List<UsbPrinterCandidate>>(emptyList())
    private val discoveryBusy = MutableStateFlow(false)
    private val discoveryError = MutableStateFlow<String?>(null)
    private val printerFormFeedback = MutableStateFlow<PrinterFormFeedback?>(null)
    private val mutablePrinterEditState = MutableStateFlow(
        savedStateHandle[PRINTER_EDIT_STATE_KEY] ?: PrinterEditUiState(),
    )
    private val mutableAllowedOriginsState = MutableStateFlow(
        savedStateHandle[ALLOWED_ORIGINS_STATE_KEY] ?: AllowedOriginsUiState(),
    )
    private val mutablePortState = MutableStateFlow(
        savedStateHandle[PORT_STATE_KEY] ?: PortUiState(),
    )
    private var networkScan: Job? = null

    val printerEditState: StateFlow<PrinterEditUiState> = mutablePrinterEditState.asStateFlow()
    val allowedOriginsState: StateFlow<AllowedOriginsUiState> = mutableAllowedOriginsState.asStateFlow()
    val portState: StateFlow<PortUiState> = mutablePortState.asStateFlow()

    init {
        viewModelScope.launch {
            app.settingsRepository.settings.collect { settings ->
                app.connectionUrlRepository.updatePort(settings.port)
                val current = mutableAllowedOriginsState.value
                when {
                    !current.initialized && !current.dirty -> {
                        setAllowedOriginsState(AllowedOriginsUiState.fromPersisted(settings.allowedOrigins))
                    }
                    !current.initialized -> {
                        setAllowedOriginsState(
                            current.copy(
                                initialized = true,
                                persistedOrigins = settings.allowedOrigins,
                                origins = if (current.origins == current.persistedOrigins) {
                                    settings.allowedOrigins
                                } else {
                                    current.origins
                                },
                            ),
                        )
                    }
                    !current.dirty && current.persistedOrigins != settings.allowedOrigins -> {
                        setAllowedOriginsState(AllowedOriginsUiState.fromPersisted(settings.allowedOrigins))
                    }
                }
                val currentPort = mutablePortState.value
                if (!currentPort.initialized ||
                    (!currentPort.dirty && currentPort.persistedPort != settings.port)
                ) {
                    setPortState(PortUiState.fromPersisted(settings.port, BuildConfig.ENROLLMENT_PORT))
                }
            }
        }
    }

    private val bridgeState = combine(
        app.settingsRepository.settings,
        app.runtimeRepository.state,
        app.connectionUrlRepository.urls,
    ) { settings, runtime, connectionUrls -> Triple(settings, runtime, connectionUrls) }

    private val printerState = combine(
        saving,
        resultMessage,
        app.printerRepository.printers,
        pairedBluetooth,
        printerBusy,
        networkCandidates,
        usbCandidates,
        discoveryBusy,
        discoveryError,
        printerFormFeedback,
    ) { values ->
        PrinterUiParts(
            saving = values[0] as Boolean,
            message = values[1] as String?,
            printers = @Suppress("UNCHECKED_CAST") (values[2] as List<PrinterDefinition>),
            paired = @Suppress("UNCHECKED_CAST") (values[3] as List<PairedBluetoothPrinter>),
            busy = values[4] as Boolean,
            networkCandidates = @Suppress("UNCHECKED_CAST") (values[5] as List<NetworkPrinterCandidate>),
            usbCandidates = @Suppress("UNCHECKED_CAST") (values[6] as List<UsbPrinterCandidate>),
            discoveryBusy = values[7] as Boolean,
            discoveryError = values[8] as String?,
            formFeedback = values[9] as PrinterFormFeedback?,
        )
    }

    private val portFeedback = combine(portSaving, portResultMessage, portResultIsError) { busy, message, error ->
        PortFeedback(busy, message, error)
    }

    val uiState = combine(
        bridgeState,
        printerState,
        app.httpsRepository.state,
        portFeedback,
    ) { bridge, printer, https, port ->
        val (settings, runtime, connectionUrls) = bridge
        BridgeUiState(
            runtime = runtime,
            token = settings.token,
            allowedOrigins = settings.allowedOrigins,
            port = settings.port,
            saving = printer.saving,
            resultMessage = printer.message,
            portSaving = port.saving,
            portResultMessage = port.message,
            portResultIsError = port.isError,
            connectionUrls = if (https.loaded && (https.enabled || https.transport == "stopped")) {
                ConnectionUrls(ConnectionUrl(https.host, connectionUrls.primary.kind), emptyList())
            } else connectionUrls,
            printers = printer.printers,
            pairedBluetooth = printer.paired,
            networkCandidates = printer.networkCandidates,
            usbCandidates = printer.usbCandidates,
            discoveryBusy = printer.discoveryBusy,
            discoveryError = printer.discoveryError,
            printerBusy = printer.busy,
            printerFormFeedback = printer.formFeedback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BridgeUiState(),
    )

    fun updateOriginInput(value: String) {
        setAllowedOriginsState(mutableAllowedOriginsState.value.withInput(value))
        resultMessage.value = null
    }

    fun commitOriginInput() {
        setAllowedOriginsState(mutableAllowedOriginsState.value.commitInput())
    }

    fun removeOrigin(origin: String) {
        setAllowedOriginsState(mutableAllowedOriginsState.value.remove(origin))
        resultMessage.value = null
    }

    fun saveOrigins() {
        val committed = mutableAllowedOriginsState.value.commitInput()
        setAllowedOriginsState(committed)
        if (committed.errorCode != null || !committed.dirty) return

        viewModelScope.launch {
            saving.value = true
            resultMessage.value = null
            runCatching { app.settingsRepository.saveAllowedOrigins(committed.origins) }
                .onSuccess { saved ->
                    setAllowedOriginsState(AllowedOriginsUiState.fromPersisted(saved.allowedOrigins))
                    BridgeForegroundService.restart(app)
                    resultMessage.value = "Orígenes guardados. Reiniciando bridge."
                }
                .onFailure { error ->
                    resultMessage.value = error.message ?: "No se pudieron guardar los orígenes."
                }
            saving.value = false
        }
    }

    fun updatePortInput(value: String) {
        setPortState(mutablePortState.value.withInput(value))
        portResultMessage.value = null
    }

    fun savePort() {
        val draft = mutablePortState.value
        val port = draft.parsedPort ?: return
        if (!draft.canSave || portSaving.value) return
        if (!BridgeForegroundService.hasRequiredLocalNetworkPermission(app)) {
            portResultMessage.value = "Concede acceso a la red local para cambiar el puerto."
            portResultIsError.value = true
            return
        }
        viewModelScope.launch {
            portSaving.value = true
            portResultMessage.value = null
            portResultIsError.value = false
            runCatching {
                BridgeForegroundService.start(app)
                kotlinx.coroutines.withTimeout(30_000) { app.runtimeRepository.changePort(port) }
            }.onSuccess {
                setPortState(PortUiState.fromPersisted(port, BuildConfig.ENROLLMENT_PORT))
                portResultMessage.value =
                    "Puerto guardado. Actualiza la URL de conexión en el POS."
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                portResultIsError.value = true
                portResultMessage.value = when (error.message) {
                    "bridge_port_in_use" -> "El puerto $port está ocupado. El bridge continúa en ${draft.persistedPort}."
                    "https_reserved_port" -> "El puerto ${BuildConfig.ENROLLMENT_PORT} está reservado para certificados."
                    else -> "No se pudo cambiar el puerto. El bridge continúa en ${draft.persistedPort}."
                }
            }
            portSaving.value = false
        }
    }

    private fun setAllowedOriginsState(state: AllowedOriginsUiState) {
        mutableAllowedOriginsState.value = state
        savedStateHandle[ALLOWED_ORIGINS_STATE_KEY] = state
    }

    private fun setPortState(state: PortUiState) {
        mutablePortState.value = state
        savedStateHandle[PORT_STATE_KEY] = state
    }

    fun refreshConnectionUrls() {
        app.connectionUrlRepository.refresh()
    }

    fun refreshPairedBluetooth() {
        discoveryError.value = null
        val discovery = app.printerDiscoveryRepository
        pairedBluetooth.value = when {
            !discovery.hasBluetoothPermission() -> {
                discoveryError.value = "bluetooth_permission_required"
                emptyList()
            }
            !discovery.isBluetoothEnabled() -> {
                discoveryError.value = "bluetooth_disabled"
                emptyList()
            }
            else -> discovery.pairedBluetooth().also {
                if (it.isEmpty()) discoveryError.value = "bluetooth_no_devices"
            }
        }
    }

    fun scanNetwork() {
        networkScan?.cancel()
        networkScan = viewModelScope.launch {
            discoveryBusy.value = true
            discoveryError.value = null
            networkCandidates.value = emptyList()
            runCatching { app.printerDiscoveryRepository.scanNetwork() }
                .onSuccess {
                    networkCandidates.value = it
                    if (it.isEmpty()) discoveryError.value = "network_no_results"
                }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) discoveryError.value = it.message }
            discoveryBusy.value = false
        }
    }

    fun cancelNetworkScan() {
        networkScan?.cancel()
        networkScan = null
        discoveryBusy.value = false
    }

    fun refreshUsb() {
        discoveryError.value = null
        runCatching { app.printerDiscoveryRepository.connectedUsb() }
            .onSuccess {
                usbCandidates.value = it
                if (it.isEmpty()) discoveryError.value = "usb_no_devices"
            }
            .onFailure {
                usbCandidates.value = emptyList()
                discoveryError.value = it.message
            }
    }

    fun authorizeUsb(deviceName: String, onAuthorized: (UsbPrinterCandidate) -> Unit) {
        viewModelScope.launch {
            discoveryBusy.value = true
            discoveryError.value = null
            runCatching { app.printerDiscoveryRepository.authorizeUsb(deviceName) }
                .onSuccess { candidate ->
                    usbCandidates.value = app.printerDiscoveryRepository.connectedUsb()
                    if (candidate.ambiguous) discoveryError.value = "usb_ambiguous_device"
                    else onAuthorized(candidate)
                }
                .onFailure { discoveryError.value = it.message }
            discoveryBusy.value = false
        }
    }

    fun clearDiscovery() {
        cancelNetworkScan()
        discoveryError.value = null
    }

    fun beginPrinterEdit(sessionId: String, printerId: String?) {
        if (mutablePrinterEditState.value.sessionId == sessionId) return
        if (printerId == null) {
            setPrinterEditState(PrinterEditUiState.newPrinter(sessionId))
            return
        }
        setPrinterEditState(PrinterEditUiState.loading(sessionId, printerId))
        viewModelScope.launch {
            val printer = app.printerRepository.find(printerId)
            if (mutablePrinterEditState.value.sessionId != sessionId) return@launch
            setPrinterEditState(
                printer?.let { PrinterEditUiState.from(sessionId, it) }
                    ?: PrinterEditUiState.notFound(sessionId, printerId),
            )
        }
    }

    fun updatePrinterForm(next: PrinterEditUiState) {
        val current = mutablePrinterEditState.value
        if (!current.loaded || current.notFound || current.printerId != next.printerId) return
        setPrinterEditState(if (
            !PrinterProfileCatalog.get(next.profileId).supportsWidth(next.width)
        ) {
            next.copy(profileId = PrinterProfileCatalog.DEFAULT_PROFILE_ID)
        } else next)
    }

    private fun setPrinterEditState(state: PrinterEditUiState) {
        mutablePrinterEditState.value = state
        savedStateHandle[PRINTER_EDIT_STATE_KEY] = state
    }

    fun savePrinter(printer: PrinterDefinition, editing: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            printerBusy.value = true
            printerFormFeedback.value = null
            runCatching {
                if (editing) app.printerRepository.update(printer) else app.printerRepository.create(printer)
            }.onSuccess {
                resultMessage.value = "Impresora guardada."
                onSaved()
            }.onFailure {
                printerFormFeedback.value = PrinterFormFeedback(printerError(it), true)
            }
            printerBusy.value = false
        }
    }

    fun testPrinterConfiguration(printer: PrinterDefinition) {
        viewModelScope.launch {
            printerBusy.value = true
            printerFormFeedback.value = null
            runCatching {
                if (printer.tipo == PrinterType.USB) {
                    app.printerDiscoveryRepository.authorizeSavedUsb(printer)
                }
                app.printCoordinator.testConfiguration(printer)
            }.onSuccess {
                printerFormFeedback.value = PrinterFormFeedback(
                    "La configuración se entregó correctamente al transporte.",
                    false,
                )
            }.onFailure {
                printerFormFeedback.value = PrinterFormFeedback(printerError(it), true)
            }
            printerBusy.value = false
        }
    }

    fun clearPrinterFormFeedback() {
        printerFormFeedback.value = null
    }

    fun deletePrinter(id: String) {
        viewModelScope.launch {
            printerBusy.value = true
            runCatching { app.printerRepository.delete(id) }
                .onSuccess { resultMessage.value = "Impresora eliminada." }
                .onFailure { resultMessage.value = printerError(it) }
            printerBusy.value = false
        }
    }

    fun testPrinter(id: String) {
        viewModelScope.launch {
            printerBusy.value = true
            resultMessage.value = null
            runCatching {
                val printer = app.printerRepository.find(id) ?: error("printer_not_found")
                if (printer.tipo == PrinterType.USB) {
                    app.printerDiscoveryRepository.authorizeSavedUsb(printer)
                }
                app.localBridgeClient.testPrinter(id)
            }
                .onSuccess { resultMessage.value = "Prueba enviada al transporte." }
                .onFailure { resultMessage.value = printerError(it) }
            printerBusy.value = false
        }
    }

    private fun printerError(error: Throwable): String = when (error.message) {
        "invalid_printer_name" -> "Escribe un nombre para la impresora."
        "invalid_printer_host" -> "Escribe una dirección de red válida."
        "invalid_printer_port" -> "El puerto debe estar entre 1 y 65535."
        "invalid_bluetooth_address" -> "Selecciona una impresora Bluetooth emparejada."
        "invalid_usb_device" -> "Selecciona una impresora USB compatible."
        "invalid_profile_encoding" -> "Selecciona un encoding válido."
        "invalid_profile_code_table" -> "La tabla de caracteres debe estar entre 0 y 255."
        "bluetooth_permission_required" -> "Falta permiso para usar la impresora Bluetooth."
        "bluetooth_disabled" -> "Bluetooth está desactivado."
        "usb_permission_required" -> "Vuelve a autorizar la impresora USB."
        "usb_disconnected" -> "La impresora USB está desconectada."
        "usb_ambiguous_device" -> "Conecta sólo una de las impresoras USB idénticas."
        "usb_incompatible" -> "El dispositivo USB no ofrece una salida compatible."
        "usb_write_failed" -> "Falló la escritura a la impresora USB."
        "local_network_permission_required" -> "Concede acceso a la red local para imprimir."
        "printer_permission_required" -> "Android bloqueó el transporte por falta de permiso. Vuelve a autorizar la impresora."
        else -> error.message ?: "No se pudo completar la operación."
    }

    private companion object {
        const val PRINTER_EDIT_STATE_KEY = "printer_edit_state"
        const val ALLOWED_ORIGINS_STATE_KEY = "allowed_origins_state"
        const val PORT_STATE_KEY = "port_state"
    }
}
