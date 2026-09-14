package com.luiscarodev.posticketbridge.ui

import android.content.ClipData
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.luiscarodev.posticketbridge.bridge.BridgeRuntimeState
import com.luiscarodev.posticketbridge.bridge.ConnectionKind
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.BluetoothDeviceKind
import com.luiscarodev.posticketbridge.domain.NativeTextPolicy
import com.luiscarodev.posticketbridge.domain.PrintProfileMode
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.UnicodeFallback
import com.luiscarodev.posticketbridge.domain.UsbPrinterCandidate
import com.luiscarodev.posticketbridge.domain.PrinterProfileCatalog
import com.luiscarodev.posticketbridge.printing.EscPosProfiles
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute

@Serializable
private data object SettingsRoute

@Serializable
private data object HttpsSetupRoute

@Serializable
private data class PrinterEditRoute(val printerId: String? = null)

private const val NAVIGATION_FADE_DURATION_MILLIS = 700

private fun navigationFadeIn(): EnterTransition = fadeIn(
    animationSpec = tween(NAVIGATION_FADE_DURATION_MILLIS),
)

private fun navigationFadeOut(): ExitTransition = fadeOut(
    animationSpec = tween(NAVIGATION_FADE_DURATION_MILLIS),
)

@Composable
fun BridgeApp(
    viewModel: BridgeViewModel = viewModel(),
    httpsViewModel: HttpsViewModel = viewModel(),
    permissionState: StateFlow<BridgePermissionUiState>? = null,
    batteryState: StateFlow<BatteryUiState>? = null,
    onRequestLocalNetworkPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenBatterySaverSettings: () -> Unit = {},
    onOpenBatteryOptimizationSettings: () -> Unit = {},
    onCompletePermissionSetup: () -> Unit = {},
    onRequestBluetoothPermission: ((() -> Unit) -> Unit) = { it() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val https by httpsViewModel.status.collectAsStateWithLifecycle()
    val httpsDraft by httpsViewModel.draft.collectAsStateWithLifecycle()
    val httpsBusy by httpsViewModel.busy.collectAsStateWithLifecycle()
    val httpsFailure by httpsViewModel.error.collectAsStateWithLifecycle()
    val printerForm by viewModel.printerEditState.collectAsStateWithLifecycle()
    val allowedOrigins by viewModel.allowedOriginsState.collectAsStateWithLifecycle()
    val defaultPermissionState = remember { MutableStateFlow(BridgePermissionUiState.Granted) }
    val permissions by (permissionState ?: defaultPermissionState).collectAsStateWithLifecycle()
    val defaultBatteryState = remember { MutableStateFlow(BatteryUiState.Unrestricted) }
    val battery by (batteryState ?: defaultBatteryState).collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var permissionGuideDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshConnectionUrls() }
    LaunchedEffect(permissions.localNetwork, permissions.notifications) {
        if (permissions.localNetwork == PermissionUiStatus.GRANTED &&
            permissions.notifications == PermissionUiStatus.GRANTED &&
            !permissions.onboardingCompleted
        ) onCompletePermissionSetup()
    }

    Box {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = { navigationFadeIn() },
            exitTransition = { navigationFadeOut() },
            popEnterTransition = { navigationFadeIn() },
            popExitTransition = { navigationFadeOut() },
            predictivePopEnterTransition = { _ -> navigationFadeIn() },
            predictivePopExitTransition = { _ -> navigationFadeOut() },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    state = state,
                    permissions = permissions,
                    battery = battery,
                    onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenBatterySaverSettings = onOpenBatterySaverSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onAddPrinter = { navController.navigate(PrinterEditRoute()) },
                    onEditPrinter = { navController.navigate(PrinterEditRoute(it)) },
                    onTestPrinter = viewModel::testPrinter,
                    onDeletePrinter = viewModel::deletePrinter,
                    httpsContent = { HttpsAccessCard(https, httpsBusy,
                        { navController.navigate(HttpsSetupRoute) }, httpsViewModel::retry) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    state = state,
                    originsState = allowedOrigins,
                    navController = navController,
                    onOriginInputChange = viewModel::updateOriginInput,
                    onCommitOrigin = viewModel::commitOriginInput,
                    onRemoveOrigin = viewModel::removeOrigin,
                    onSaveOrigins = viewModel::saveOrigins,
                    httpsContent = { HttpsSettingsCard(https, httpsDraft, httpsBusy, httpsFailure,
                        httpsViewModel::enable, httpsViewModel::select, httpsViewModel::save, httpsViewModel::reset) },
                )
            }
            composable<HttpsSetupRoute> {
                HttpsSetupScreen(https, httpsBusy, httpsFailure, { navController.popBackStack() },
                    httpsViewModel::enroll, httpsViewModel::stopEnrollment)
            }
            composable<PrinterEditRoute> { entry ->
                val route = entry.toRoute<PrinterEditRoute>()
                LaunchedEffect(entry.id, route.printerId) {
                    viewModel.beginPrinterEdit(entry.id, route.printerId)
                }
                PrinterEditScreen(
                    state = state,
                    form = printerForm,
                    editorSessionId = entry.id,
                    permissions = permissions,
                    printerId = route.printerId,
                    navController = navController,
                    onOpenAppSettings = onOpenAppSettings,
                    onRequestBluetoothPermission = {
                        onRequestBluetoothPermission { viewModel.refreshPairedBluetooth() }
                    },
                    onScanNetwork = {
                        if (permissions.localNetwork == PermissionUiStatus.GRANTED) {
                            viewModel.scanNetwork()
                        } else if (permissions.localNetwork == PermissionUiStatus.SETTINGS_REQUIRED) {
                            onOpenAppSettings()
                        } else onRequestLocalNetworkPermission()
                    },
                    onCancelNetworkScan = viewModel::cancelNetworkScan,
                    onRefreshUsb = viewModel::refreshUsb,
                    onAuthorizeUsb = viewModel::authorizeUsb,
                    onClearDiscovery = viewModel::clearDiscovery,
                    onClearFormFeedback = viewModel::clearPrinterFormFeedback,
                    onTestConfiguration = viewModel::testPrinterConfiguration,
                    onFormChange = viewModel::updatePrinterForm,
                    onSave = { printer, editing ->
                        viewModel.savePrinter(printer, editing) { navController.popBackStack() }
                    },
                )
            }
        }

        val mustGuide = !permissionGuideDismissed &&
            (!permissions.onboardingCompleted || permissions.localNetwork != PermissionUiStatus.GRANTED)
        if (mustGuide) {
            PermissionSetupDialog(
                permissions = permissions,
                onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenAppSettings = onOpenAppSettings,
                onContinue = {
                    permissionGuideDismissed = true
                    onCompletePermissionSetup()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    httpsContent: @Composable () -> Unit,
    state: BridgeUiState,
    permissions: BridgePermissionUiState,
    battery: BatteryUiState,
    onRequestLocalNetworkPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySaverSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddPrinter: () -> Unit,
    onEditPrinter: (String) -> Unit,
    onTestPrinter: (String) -> Unit,
    onDeletePrinter: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.resultMessage) {
        state.resultMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("POS Ticket Bridge") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Abrir ajustes")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPrinter) {
                Icon(Icons.Default.Add, contentDescription = "Agregar impresora")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (permissions.localNetwork != PermissionUiStatus.GRANTED ||
                permissions.notifications != PermissionUiStatus.GRANTED
            ) {
                PermissionStatusCard(
                    permissions = permissions,
                    onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
            if (battery.isPowerSaveMode) {
                BatteryWarningCard(
                    title = "Ahorro de batería activo",
                    description = "Android puede limitar la red y el trabajo en segundo plano. Desactívalo mientras uses el bridge.",
                    actionLabel = "Revisar ahorro de batería",
                    onAction = onOpenBatterySaverSettings,
                )
            }
            if (!battery.isIgnoringBatteryOptimizations) {
                BatteryWarningCard(
                    title = "Optimización de batería aplicada",
                    description = "El sistema puede suspender POS Ticket Bridge con la pantalla apagada. Permite que funcione sin restricciones.",
                    actionLabel = "Revisar optimización",
                    onAction = onOpenBatteryOptimizationSettings,
                )
            }
            RuntimeCard(state.runtime, state.port)
            CompactConnectionCard(state, snackbarHostState)
            httpsContent()
            SavedPrintersSection(
                state = state,
                onEdit = onEditPrinter,
                onTest = onTestPrinter,
                onDelete = onDeletePrinter,
            )
        }
    }
}

@Composable
private fun BatteryWarningCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun PermissionSetupDialog(
    permissions: BridgePermissionUiState,
    onRequestLocalNetworkPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    val needsLocalNetwork = permissions.localNetwork != PermissionUiStatus.GRANTED
    val status = if (needsLocalNetwork) permissions.localNetwork else permissions.notifications
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (needsLocalNetwork) "Acceso a la red local" else "Notificaciones del bridge") },
        text = {
            Text(
                if (needsLocalNetwork) {
                    "POS Ticket Bridge necesita comunicarse con el sistema POS y las impresoras de tu red. Sin este acceso puedes configurar la app, pero el bridge permanecerá detenido."
                } else {
                    "Permite las notificaciones para ver que el bridge sigue activo. Puedes continuar sin ellas; Android mantendrá el servicio visible en su administrador de tareas."
                },
            )
        },
        confirmButton = {
            Button(
                onClick = if (status == PermissionUiStatus.SETTINGS_REQUIRED) {
                    onOpenAppSettings
                } else if (needsLocalNetwork) {
                    onRequestLocalNetworkPermission
                } else {
                    onRequestNotificationPermission
                },
            ) {
                Text(if (status == PermissionUiStatus.SETTINGS_REQUIRED) "Abrir ajustes" else "Permitir")
            }
        },
        dismissButton = {
            TextButton(onClick = onContinue) {
                Text(if (needsLocalNetwork) "Configurar sin activar" else "Continuar sin notificaciones")
            }
        },
    )
}

@Composable
private fun PermissionStatusCard(
    permissions: BridgePermissionUiState,
    onRequestLocalNetworkPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val localMissing = permissions.localNetwork != PermissionUiStatus.GRANTED
    val status = if (localMissing) permissions.localNetwork else permissions.notifications
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (localMissing) {
                MaterialTheme.colorScheme.errorContainer
            } else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (localMissing) "Bridge detenido: falta acceso a red local" else "Notificaciones desactivadas",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (localMissing) {
                    "Concede el permiso para recibir solicitudes del POS y usar impresoras de red."
                } else {
                    "El bridge funciona, pero no verás su estado en el panel de notificaciones."
                },
            )
            TextButton(
                onClick = if (status == PermissionUiStatus.SETTINGS_REQUIRED) {
                    onOpenAppSettings
                } else if (localMissing) {
                    onRequestLocalNetworkPermission
                } else {
                    onRequestNotificationPermission
                },
            ) {
                Text(if (status == PermissionUiStatus.SETTINGS_REQUIRED) "Abrir ajustes" else "Conceder permiso")
            }
        }
    }
}

@Composable
private fun CompactConnectionCard(state: BridgeUiState, snackbarHostState: SnackbarHostState) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val primary = state.connectionUrls.primary
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactValue(
                    label = "Host · ${connectionLabel(primary.kind)}",
                    value = primary.value.ifEmpty { "Sin conexión" },
                    modifier = Modifier.weight(1f),
                )
                CopyIconButton(primary.value, "Copiar host", enabled = primary.value.isNotEmpty()) {
                    snackbarHostState.showSnackbar("Host copiado")
                }
                if (state.connectionUrls.alternatives.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Otras conexiones")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            state.connectionUrls.alternatives.forEach { address ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(connectionLabel(address.kind))
                                            Text(
                                                address.value,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("Host", address.value)),
                                            )
                                            snackbarHostState.showSnackbar("Host copiado")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactValue(
                    label = "Token",
                    value = state.token.ifEmpty { "Generando…" },
                    modifier = Modifier.weight(1f),
                )
                CopyIconButton(state.token, "Copiar token", enabled = state.token.isNotEmpty()) {
                    snackbarHostState.showSnackbar("Token copiado")
                }
            }
        }
    }
}

@Composable
private fun CompactValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SavedPrintersSection(
    state: BridgeUiState,
    onEdit: (String) -> Unit,
    onTest: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val normalizedQuery = query.trim()
    val visiblePrinters = state.printers.filter { printer ->
        normalizedQuery.isEmpty() || listOfNotNull(
            printer.nombre,
            printer.id,
            printer.tipo.name,
            printer.host,
            printer.bluetoothName,
            printer.bluetoothAddress,
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Impresoras guardadas", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                    }
                }
            } else {
                null
            },
            placeholder = { Text("Buscar impresora") },
        )

        when {
            state.printers.isEmpty() -> Text("Aún no hay impresoras. Usa + para agregar la primera.")
            visiblePrinters.isEmpty() -> Text("No hay impresoras que coincidan con la búsqueda.")
            else -> visiblePrinters.forEach { printer ->
                SavedPrinterCard(
                    printer = printer,
                    busy = state.printerBusy,
                    onTest = { onTest(printer.id) },
                    onEdit = { onEdit(printer.id) },
                    onDelete = { deleteId = printer.id },
                )
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Eliminar impresora") },
            text = { Text("La impresora dejará de estar disponible para el POS.") },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deleteId = null }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SavedPrinterCard(
    printer: PrinterDefinition,
    busy: Boolean,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by rememberSaveable(printer.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(printer.nombre, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${printer.id} · ${printer.tipo.name.lowercase()} · ${printer.anchoMm} mm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (printer.enabled) "Habilitada" else "Deshabilitada",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (printer.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            IconButton(enabled = !busy && printer.enabled, onClick = onTest) {
                Icon(Icons.Default.Print, contentDescription = "Probar ${printer.nombre}")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${printer.nombre}")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    httpsContent: @Composable () -> Unit,
    state: BridgeUiState,
    originsState: AllowedOriginsUiState,
    navController: NavHostController,
    onOriginInputChange: (String) -> Unit,
    onCommitOrigin: () -> Unit,
    onRemoveOrigin: (String) -> Unit,
    onSaveOrigins: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Red", style = MaterialTheme.typography.titleLarge)
            httpsContent()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Orígenes CORS autorizados", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Agrega los sitios que podrán llamar al bridge desde un navegador.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (originsState.origins.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            originsState.origins.forEach { origin ->
                                InputChip(
                                    selected = false,
                                    enabled = !state.saving,
                                    onClick = { onRemoveOrigin(origin) },
                                    label = {
                                        Text(
                                            text = origin,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Eliminar $origin",
                                        )
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = originsState.input,
                        onValueChange = onOriginInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("allowed-origin-input"),
                        enabled = originsState.initialized && !state.saving,
                        singleLine = true,
                        isError = originsState.errorCode != null,
                        label = { Text("Agregar origen") },
                        placeholder = { Text("https://pos.ejemplo.com") },
                        supportingText = {
                            Text(
                                if (originsState.errorCode != null) {
                                    "Usa http:// o https://, sin ruta, parámetros ni credenciales."
                                } else {
                                    "Pulsa Enter o +. Puedes pegar varios separados por coma o salto de línea."
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onCommitOrigin() }),
                        trailingIcon = {
                            IconButton(
                                enabled = originsState.input.isNotBlank(),
                                onClick = onCommitOrigin,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar origen")
                            }
                        },
                    )
                    Text(
                        "Los orígenes locales del bridge (localhost y 127.0.0.1) ya están autorizados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = originsState.initialized && !state.saving && originsState.dirty,
                        onClick = onSaveOrigins,
                        modifier = Modifier.testTag("save-origins"),
                    ) {
                        Text(if (state.saving) "Guardando…" else "Guardar cambios")
                    }
                    state.resultMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyIconButton(
    value: String,
    contentDescription: String,
    enabled: Boolean = true,
    onCopied: suspend () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    IconButton(
        enabled = enabled,
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(contentDescription, value)))
                onCopied()
            }
        },
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = contentDescription)
    }
}

@Composable
private fun RuntimeCard(runtime: BridgeRuntimeState, port: Int) {
    val (label, isError) = when (runtime) {
        BridgeRuntimeState.Stopped -> "Detenido" to false
        BridgeRuntimeState.Starting -> "Iniciando…" to false
        is BridgeRuntimeState.Running -> "Activo en el puerto $port" to false
        is BridgeRuntimeState.Failed -> (if (runtime.reason.startsWith("https_")) httpsError(runtime.reason)
            else "Error: ${runtime.reason}") to true
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Estado", style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun connectionLabel(kind: ConnectionKind): String = when (kind) {
    ConnectionKind.WIFI -> "Red Wi‑Fi"
    ConnectionKind.ETHERNET -> "Red Ethernet"
    ConnectionKind.HOTSPOT -> "Punto de acceso"
    ConnectionKind.LOCAL -> "Sólo este teléfono"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterEditScreen(
    state: BridgeUiState,
    form: PrinterEditUiState,
    editorSessionId: String,
    permissions: BridgePermissionUiState,
    printerId: String?,
    navController: NavHostController,
    onOpenAppSettings: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onScanNetwork: () -> Unit,
    onCancelNetworkScan: () -> Unit,
    onRefreshUsb: () -> Unit,
    onAuthorizeUsb: (String, (UsbPrinterCandidate) -> Unit) -> Unit,
    onClearDiscovery: () -> Unit,
    onClearFormFeedback: () -> Unit,
    onTestConfiguration: (PrinterDefinition) -> Unit,
    onFormChange: (PrinterEditUiState) -> Unit,
    onSave: (PrinterDefinition, Boolean) -> Unit,
) {
    if (form.sessionId != editorSessionId || form.printerId != printerId || !form.loaded) {
        PrinterEditorUnavailableScreen("Cargando impresora…", navController)
        return
    }
    if (form.notFound) {
        PrinterEditorUnavailableScreen("La impresora ya no existe.", navController)
        return
    }

    val editing = form.editing
    val context = LocalContext.current
    var selectingConnection by rememberSaveable(printerId) { mutableStateOf(!editing) }
    var discoveryType by rememberSaveable(printerId) { mutableStateOf<PrinterType?>(null) }
    var customSettingsExpanded by rememberSaveable(printerId) {
        mutableStateOf(form.profileMode == PrintProfileMode.CUSTOM)
    }

    LaunchedEffect(printerId) { onClearFormFeedback() }

    fun chooseConnection(chosen: PrinterType) {
        discoveryType = chosen
        onClearDiscovery()
        when (chosen) {
            PrinterType.BLUETOOTH -> onRequestBluetoothPermission()
            PrinterType.USB -> onRefreshUsb()
            PrinterType.NETWORK -> Unit
        }
    }
    val backAction = {
        when {
            selectingConnection && discoveryType != null -> {
                onCancelNetworkScan()
                discoveryType = null
            }
            selectingConnection && editing -> selectingConnection = false
            else -> navController.popBackStack()
        }
        Unit
    }
    BackHandler(onBack = backAction)
    DisposableEffect(Unit) { onDispose(onCancelNetworkScan) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (selectingConnection && editing) "Elegir conexión · Paso 1 de 2"
                        else if (selectingConnection) "Agregar impresora · Paso 1 de 2"
                        else if (editing) "Editar impresora" else "Paso 2 de 2",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = backAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectingConnection) {
                if (discoveryType == null) {
                    Text("¿Cómo está conectada?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Elige una opción para encontrar la impresora.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConnectionTypeCard("Red", "Buscar impresoras con puerto 9100 o ingresar una IP.", Icons.Default.Wifi) {
                        chooseConnection(PrinterType.NETWORK)
                    }
                    ConnectionTypeCard("Bluetooth", "Usar una impresora previamente emparejada.", Icons.Default.Bluetooth) {
                        chooseConnection(PrinterType.BLUETOOTH)
                    }
                    ConnectionTypeCard("USB", "Detectar una impresora conectada por USB u OTG.", Icons.Default.Usb) {
                        chooseConnection(PrinterType.USB)
                    }
                } else {
                    Text(discoveryTitle(discoveryType!!), style = MaterialTheme.typography.headlineSmall)
                    when (requireNotNull(discoveryType)) {
                        PrinterType.NETWORK -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !state.discoveryBusy, onClick = onScanNetwork) {
                                    Icon(Icons.Default.Search, null)
                                    Text("Buscar en la red", Modifier.padding(start = 8.dp))
                                }
                                if (state.discoveryBusy) TextButton(onClick = onCancelNetworkScan) { Text("Cancelar") }
                            }
                            if (state.discoveryBusy) DiscoveryProgress("Buscando en la red local…")
                            state.networkCandidates.forEach { candidate ->
                                DiscoveryResult("Dispositivo de red", "${candidate.host}:${candidate.port}") {
                                    onFormChange(
                                        form.copy(
                                            type = PrinterType.NETWORK,
                                            host = candidate.host,
                                            port = candidate.port.toString(),
                                            name = form.name.ifBlank { "Impresora ${candidate.host}" },
                                        ),
                                    )
                                    selectingConnection = false
                                }
                            }
                            HorizontalDivider()
                            Text("Ingresar IP manualmente", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(form.host, { onFormChange(form.copy(host = it)) }, Modifier.fillMaxWidth().testTag("network_host"), singleLine = true, label = { Text("IP o host") })
                            OutlinedTextField(form.port, { onFormChange(form.copy(port = it.filter(Char::isDigit))) }, Modifier.fillMaxWidth().testTag("network_port"), singleLine = true, label = { Text("Puerto") })
                            Button(enabled = form.host.isNotBlank() && form.port.toIntOrNull() in 1..65535, onClick = {
                                onFormChange(
                                    form.copy(
                                        type = PrinterType.NETWORK,
                                        name = form.name.ifBlank { "Impresora ${form.host}" },
                                    ),
                                )
                                selectingConnection = false
                            }) { Text("Usar esta dirección") }
                        }
                        PrinterType.BLUETOOTH -> {
                            Text("Sólo aparecen dispositivos emparejados en Android.")
                            if (permissions.bluetooth != PermissionUiStatus.GRANTED) {
                                Text(
                                    "Concede acceso a dispositivos cercanos para mostrar las impresoras emparejadas.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TextButton(
                                    onClick = if (permissions.bluetooth == PermissionUiStatus.SETTINGS_REQUIRED) {
                                        onOpenAppSettings
                                    } else onRequestBluetoothPermission,
                                ) {
                                    Text(
                                        if (permissions.bluetooth == PermissionUiStatus.SETTINGS_REQUIRED) {
                                            "Abrir permisos de la app"
                                        } else "Conceder permiso Bluetooth",
                                    )
                                }
                            }
                            state.pairedBluetooth.forEach { device ->
                                DiscoveryResult(
                                    device.name,
                                    "${bluetoothKindLabel(device.kind)} · ${device.address}",
                                    icon = bluetoothKindIcon(device.kind),
                                ) {
                                    onFormChange(
                                        form.copy(
                                            type = PrinterType.BLUETOOTH,
                                            bluetoothAddress = device.address,
                                            bluetoothName = device.name,
                                            name = form.name.ifBlank { device.name },
                                        ),
                                    )
                                    selectingConnection = false
                                }
                            }
                            TextButton(onClick = {
                                context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS))
                            }) { Text("Abrir ajustes de Bluetooth") }
                            TextButton(
                                enabled = permissions.bluetooth == PermissionUiStatus.GRANTED,
                                onClick = onRequestBluetoothPermission,
                            ) {
                                Icon(Icons.Default.Refresh, null); Text("Actualizar")
                            }
                        }
                        PrinterType.USB -> {
                            state.usbCandidates.forEach { candidate ->
                                DiscoveryResult(
                                    candidate.name,
                                    "VID %04X · PID %04X".format(candidate.vendorId, candidate.productId),
                                    enabled = !candidate.ambiguous && !state.discoveryBusy,
                                ) {
                                    onAuthorizeUsb(candidate.deviceName) { authorized ->
                                        val suggestedProfile = PrinterProfileCatalog.suggestedForUsb(
                                            authorized.vendorId,
                                            authorized.productId,
                                        )
                                        onFormChange(
                                            form.copy(
                                                type = PrinterType.USB,
                                                usbVendorId = authorized.vendorId,
                                                usbProductId = authorized.productId,
                                                usbSerialNumber = authorized.serialNumber,
                                                usbName = authorized.name,
                                                name = form.name.ifBlank { authorized.name },
                                                profileId = suggestedProfile?.id ?: form.profileId,
                                            ),
                                        )
                                        selectingConnection = false
                                    }
                                }
                                if (candidate.ambiguous) Text("No se puede distinguir de otro USB idéntico sin número de serie.", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(enabled = !state.discoveryBusy, onClick = onRefreshUsb) {
                                Icon(Icons.Default.Refresh, null); Text("Actualizar USB")
                            }
                            if (state.discoveryBusy) DiscoveryProgress("Solicitando permiso USB…")
                        }
                    }
                    discoveryMessage(state.discoveryError)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                ConnectionSummary(
                    label = printerTypeLabel(form.type),
                    detail = connectionSummary(
                        form.type,
                        form.host,
                        form.port,
                        form.bluetoothName,
                        form.bluetoothAddress,
                        form.usbName,
                        form.usbVendorId,
                        form.usbProductId,
                    ),
                    onChange = { selectingConnection = true; discoveryType = null },
                )
                OutlinedTextField(form.name, { onFormChange(form.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre") })
                Text("Ancho del papel", style = MaterialTheme.typography.titleMedium)
                ChoiceRow("58 mm", form.width == 58) { onFormChange(form.copy(width = 58)) }
                ChoiceRow("80 mm", form.width == 80) { onFormChange(form.copy(width = 80)) }
                Text("Perfil de impresión", style = MaterialTheme.typography.titleMedium)
                PrintProfileSelector(
                    profileId = form.profileId,
                    custom = form.profileMode == PrintProfileMode.CUSTOM,
                    widthMm = form.width,
                    onSelectPreset = { selected ->
                        val selectedProfile = PrinterProfileCatalog.get(selected)
                        onFormChange(
                            form.copy(
                                profileId = selected,
                                profileMode = PrintProfileMode.AUTO,
                                profileLanguage = if (selectedProfile.spanishLatin != null) {
                                    PrinterLanguage.ES
                                } else form.profileLanguage,
                            ),
                        )
                        customSettingsExpanded = false
                    },
                    onSelectCustom = {
                        onFormChange(form.copy(profileMode = PrintProfileMode.CUSTOM))
                        customSettingsExpanded = true
                    },
                )
                Text("Idioma del ticket", style = MaterialTheme.typography.titleMedium)
                ChoiceRow("Español", form.profileLanguage == PrinterLanguage.ES) {
                    onFormChange(form.copy(profileLanguage = PrinterLanguage.ES))
                }
                ChoiceRow("English", form.profileLanguage == PrinterLanguage.EN) {
                    onFormChange(form.copy(profileLanguage = PrinterLanguage.EN))
                }
                if (form.profileMode == PrintProfileMode.AUTO) {
                    Text(
                        effectiveProfileDescription(form.profileId, form.profileLanguage),
                        modifier = Modifier.testTag("effective_profile"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CheckRow("Abre cajón", form.opensDrawer) { onFormChange(form.copy(opensDrawer = it)) }
                CheckRow("Impresora habilitada", form.enabled) { onFormChange(form.copy(enabled = it)) }

                if (form.profileMode == PrintProfileMode.CUSTOM) Card(
                    modifier = Modifier.fillMaxWidth().testTag("custom_profile_settings"),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        TextButton(
                            onClick = { customSettingsExpanded = !customSettingsExpanded },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Configuración personalizada", Modifier.weight(1f))
                            Icon(if (customSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                        if (customSettingsExpanded) {
                            Text("Encoding", style = MaterialTheme.typography.titleSmall)
                            EscPosProfiles.encodings.keys.forEach { encoding ->
                                ChoiceRow(encoding, form.customEncoding == encoding) {
                                    onFormChange(
                                        form.copy(
                                            customEncoding = encoding,
                                            customCodeTable = EscPosProfiles.defaultCodeTable(encoding).toString(),
                                        ),
                                    )
                                }
                            }
                            OutlinedTextField(
                                form.customCodeTable,
                                { onFormChange(form.copy(customCodeTable = it.filter(Char::isDigit).take(3))) },
                                Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Code table (0–255)") },
                            )
                            Text("Estrategia Unicode", style = MaterialTheme.typography.titleSmall)
                            UnicodeFallback.entries.forEach { fallback ->
                                ChoiceRow(unicodeFallbackLabel(fallback), form.unicodeFallback == fallback) {
                                    onFormChange(form.copy(unicodeFallback = fallback))
                                }
                            }
                        }
                    }
                }
                state.printerFormFeedback?.let { feedback ->
                    Text(
                        feedback.message,
                        color = if (feedback.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedButton(
                    enabled = !state.printerBusy && form.connectionValid && form.profileValid,
                    onClick = { onTestConfiguration(form.toDefinition()) },
                    modifier = Modifier.fillMaxWidth().testTag("test_printer_configuration"),
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Text(if (state.printerBusy) "Probando…" else "Probar configuración", Modifier.padding(start = 8.dp))
                }
                Button(
                    enabled = !state.printerBusy &&
                        form.name.isNotBlank() &&
                        form.connectionValid &&
                        form.profileValid &&
                        (!form.editing || form.dirty),
                    onClick = {
                        onSave(form.toDefinition(), editing)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.printerBusy) "Guardando…" else "Guardar impresora") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterEditorUnavailableScreen(message: String, navController: NavHostController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Impresora") },
                navigationIcon = { BackButton(navController) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrintProfileSelector(
    profileId: String,
    custom: Boolean,
    widthMm: Int,
    onSelectPreset: (String) -> Unit,
    onSelectCustom: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedTitle = if (custom) "Personalizado" else profileTitle(profileId)
    val selectedDescription = if (custom) profileDescription("custom") else profileDescription(profileId)
    val presets = PrinterProfileCatalog.selectable(widthMm)

    Box(Modifier.fillMaxWidth()) {
        Card(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().testTag("profile_selector"),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(selectedTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        selectedDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Default.ExpandMore, contentDescription = "Cambiar perfil")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(candidate.name.es)
                            Text(candidate.description.es, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { expanded = false; onSelectPreset(candidate.id) },
                )
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Personalizado")
                        Text(profileDescription("custom"), style = MaterialTheme.typography.bodySmall)
                    }
                },
                onClick = { expanded = false; onSelectCustom() },
            )
        }
    }
}

@Composable
private fun ConnectionTypeCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DiscoveryResult(
    title: String,
    description: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DiscoveryProgress(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(label)
    }
}

@Composable
private fun ConnectionSummary(label: String, detail: String, onChange: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onChange) { Text("Cambiar") }
        }
    }
}

private fun discoveryTitle(type: PrinterType) = when (type) {
    PrinterType.NETWORK -> "Buscar en la red"
    PrinterType.BLUETOOTH -> "Bluetooth emparejado"
    PrinterType.USB -> "Impresoras USB conectadas"
}

private fun printerTypeLabel(type: PrinterType) = when (type) {
    PrinterType.NETWORK -> "Red"
    PrinterType.BLUETOOTH -> "Bluetooth Classic"
    PrinterType.USB -> "USB"
}

private fun bluetoothKindIcon(kind: BluetoothDeviceKind): ImageVector = when (kind) {
    BluetoothDeviceKind.PRINTER -> Icons.Default.Print
    BluetoothDeviceKind.AUDIO -> Icons.Default.Headphones
    BluetoothDeviceKind.PHONE -> Icons.Default.PhoneAndroid
    BluetoothDeviceKind.COMPUTER -> Icons.Default.Computer
    BluetoothDeviceKind.INPUT -> Icons.Default.Keyboard
    BluetoothDeviceKind.NETWORK -> Icons.Default.Router
    BluetoothDeviceKind.WEARABLE -> Icons.Default.Watch
    BluetoothDeviceKind.IMAGING -> Icons.Default.PhotoCamera
    BluetoothDeviceKind.OTHER -> Icons.Default.DevicesOther
}

private fun bluetoothKindLabel(kind: BluetoothDeviceKind): String = when (kind) {
    BluetoothDeviceKind.PRINTER -> "Posible impresora"
    BluetoothDeviceKind.AUDIO -> "Audio"
    BluetoothDeviceKind.PHONE -> "Teléfono"
    BluetoothDeviceKind.COMPUTER -> "Computadora"
    BluetoothDeviceKind.INPUT -> "Teclado, mouse o periférico"
    BluetoothDeviceKind.NETWORK -> "Dispositivo de red"
    BluetoothDeviceKind.WEARABLE -> "Dispositivo wearable"
    BluetoothDeviceKind.IMAGING -> "Dispositivo de imagen"
    BluetoothDeviceKind.OTHER -> "Tipo no informado"
}

private fun connectionSummary(
    type: PrinterType,
    host: String,
    port: String,
    bluetoothName: String,
    bluetoothAddress: String,
    usbName: String,
    usbVendorId: Int?,
    usbProductId: Int?,
) = when (type) {
    PrinterType.NETWORK -> "$host:$port"
    PrinterType.BLUETOOTH -> "${bluetoothName.ifBlank { "Dispositivo" }} · $bluetoothAddress"
    PrinterType.USB -> usbName.ifBlank {
        "VID %04X · PID %04X".format(usbVendorId ?: 0, usbProductId ?: 0)
    }
}

private fun discoveryMessage(code: String?) = when (code) {
    "local_network_permission_required" -> "Se necesita permiso para buscar dispositivos en la red local."
    "network_unavailable" -> "Conecta el teléfono a una red Wi‑Fi o Ethernet."
    "network_no_results" -> "No se encontraron dispositivos con el puerto 9100 abierto. Puedes ingresar la IP manualmente."
    "bluetooth_permission_required" -> "Concede permiso para ver los dispositivos Bluetooth emparejados."
    "bluetooth_disabled" -> "Activa Bluetooth para continuar."
    "bluetooth_no_devices" -> "No hay impresoras Bluetooth emparejadas. Empareja una desde los ajustes de Android."
    "usb_no_devices" -> "No hay impresoras USB compatibles conectadas. Revisa el adaptador OTG."
    "usb_incompatible" -> "El dispositivo USB conectado no ofrece una salida Bulk OUT compatible con impresión."
    "usb_permission_denied" -> "Android no concedió acceso a la impresora USB."
    "usb_ambiguous_device" -> "Hay impresoras USB idénticas sin número de serie. Conecta y configura una por vez."
    "usb_disconnected" -> "La impresora USB se desconectó."
    null -> null
    else -> "No se pudo completar la búsqueda: $code"
}

private fun unicodeFallbackLabel(value: UnicodeFallback) = when (value) {
    UnicodeFallback.AUTO -> "Automático"
    UnicodeFallback.RASTER -> "Rasterizar texto"
    UnicodeFallback.NATIVE -> "Sólo nativo"
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected = selected, onClick = onSelect)
        TextButton(onClick = onSelect) { Text(label) }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun BackButton(navController: NavHostController) {
    IconButton(onClick = { navController.popBackStack() }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
    }
}

private fun profileTitle(id: String) = PrinterProfileCatalog.get(id).name.es

private fun profileDescription(id: String) = if (id == "custom") {
    "Define encoding, code table y estrategia Unicode."
} else PrinterProfileCatalog.get(id).description.es

private fun effectiveProfileDescription(id: String, language: PrinterLanguage): String {
    val profile = PrinterProfileCatalog.get(id)
    val effective = profile.encodingFor(language)
    val languageLabel = if (profile.spanishLatin == null) null else when (language) {
        PrinterLanguage.ES -> "Español"
        PrinterLanguage.EN -> "English"
    }
    val nativeBehavior = if (effective.nativePolicy == NativeTextPolicy.ASCII) {
        "caracteres no ASCII rasterizados"
    } else {
        "texto compatible nativo"
    }
    return listOfNotNull(
        "Efectivo:",
        languageLabel,
        effective.encoding,
        "tabla ${effective.codeTable}",
        nativeBehavior,
    ).joinToString(" · ").replace("Efectivo: · ", "Efectivo: ")
}
