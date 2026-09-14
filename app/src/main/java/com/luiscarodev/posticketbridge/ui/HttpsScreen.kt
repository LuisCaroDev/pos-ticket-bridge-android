package com.luiscarodev.posticketbridge.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.luiscarodev.posticketbridge.bridge.https.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun HttpsSettingsCard(status: HttpsStatus, draft: HttpsDraft, busy: Boolean, error: String?,
    onEnable: (Boolean) -> Unit, onSelect: (HttpsNetwork) -> Unit, onSave: () -> Unit, onReset: () -> Unit) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Conexión segura", style = MaterialTheme.typography.titleMedium)
            Text("Activa HTTPS para conectar tu POS desde otros dispositivos de la misma red.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HTTPS local", Modifier.weight(1f))
                Switch(draft.enabled, onEnable, enabled = draft.initialized && !busy)
            }
            if (draft.enabled) {
                Text("Red e IPv4", style = MaterialTheme.typography.labelLarge)
                if (status.networks.isEmpty()) Text("Conecta el teléfono a Wi-Fi, Ethernet o activa un punto de acceso.")
                status.networks.forEach { network ->
                    val selected = draft.name == network.name && draft.address == network.address
                    Row(Modifier.fillMaxWidth().selectable(selected, enabled = !busy, role = Role.RadioButton,
                        onClick = { onSelect(network) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null)
                        Text("${network.name} · ${network.address}", Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text("Se aplica al guardar. Desactivar HTTPS conserva los certificados instalados.", style = MaterialTheme.typography.bodySmall)
            val dirty = draft.enabled != status.enabled || draft.name != status.selection?.name || draft.address != status.selection?.address
            Button(onSave, enabled = draft.initialized && dirty && !busy) { Text(if (busy) "Guardando…" else "Guardar conexión") }
            error?.let { Text(httpsError(it), color = MaterialTheme.colorScheme.error) }
            if (status.configured || status.error != null) TextButton({ confirmReset = true }, enabled = !busy) { Text("Restablecer HTTPS") }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("¿Restablecer HTTPS?") },
        text = { Text("Se eliminarán la CA y las claves del bridge mobile. Se conservarán el token y las impresoras. Deberás instalar la nueva CA en tus dispositivos y quitar la anterior desde sus ajustes.") },
        confirmButton = { TextButton({ confirmReset = false; onReset() }) { Text("Restablecer") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancelar") } })
}

@Composable
fun HttpsAccessCard(status: HttpsStatus, busy: Boolean, onSetup: () -> Unit, onRetry: () -> Unit) {
    if (!status.enabled && status.error == null) return
    var details by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("HTTPS local · ${if (status.transport == "https") "Activo" else "Sin conexión"}", style = MaterialTheme.typography.titleMedium)
            Text("Instala el certificado en los dispositivos que usarán tu POS.")
            status.error?.let {
                Text(httpsError(it), color = MaterialTheme.colorScheme.error)
                TextButton(onRetry, enabled = !busy) { Text("Reintentar conexión") }
            }
            Button(onSetup, enabled = !busy && status.transport == "https") { Text("Configurar dispositivo") }
            TextButton({ details = !details }) { Text(if (details) "Ocultar detalles" else "Detalles técnicos") }
            if (details) {
                status.expiresAt?.let { Text("Certificado válido hasta ${formatDate(it)}") }
                status.caExpiresAt?.let { Text("CA válida hasta ${formatDate(it)}") }
                status.fingerprint?.let { CopyHttpsValue("Huella SHA-256 de la CA", it) }
                Text("Si cambia la IP, actualiza la dirección del bridge en el POS. La CA instalada se conserva.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpsSetupScreen(status: HttpsStatus, busy: Boolean, error: String?, onBack: () -> Unit,
    onEnroll: (ClientOs) -> Unit, onStop: () -> Unit,
    setup: HttpsSetupConfiguration = HttpsSetupConfiguration.bundled) {
    var osName by rememberSaveable { mutableStateOf(ClientOs.ANDROID.name) }
    var step by rememberSaveable { mutableStateOf(1) }
    var details by rememberSaveable { mutableStateOf(false) }
    val os = ClientOs.valueOf(osName)
    val context = LocalContext.current
    var openError by remember { mutableStateOf(false) }
    fun open(url: String) {
        openError = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure
    }
    DisposableEffect(Unit) { onDispose { onStop() } }
    LaunchedEffect(status.enrollment) { if (status.enrollment != null) step = 2 }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val session = status.enrollment?.takeIf { it.os == os && it.expiresAt > now }
    val qr by produceState<Bitmap?>(null, session?.url) {
        value = null
        session?.let { active -> value = withContext(Dispatchers.Default) {
            val matrix = MultiFormatWriter().encode(active.url, BarcodeFormat.QR_CODE, 640, 640)
            val pixels = IntArray(640 * 640) { index -> if (matrix[index % 640, index / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(pixels, 640, 640, Bitmap.Config.ARGB_8888)
        } }
    }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Configurar dispositivo") }, navigationIcon = {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Paso $step de 2 · ${if (step == 1) "Elige el sistema" else "Instala el certificado"}", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { step / 2f }, modifier = Modifier.fillMaxWidth())
            if (step == 1) {
                Text("Elige el sistema del dispositivo que abrirá tu POS. Conecta ambos a la misma red.")
                ClientOs.entries.forEach { item ->
                    Row(Modifier.fillMaxWidth().selectable(os == item, enabled = !busy, role = Role.RadioButton,
                        onClick = { osName = item.name }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(os == item, null)
                        Text(item.label, Modifier.padding(start = 12.dp))
                    }
                }
                val seconds = (setup.durationMs + 999) / 1000
                val duration = if (seconds % 60 == 0L) "${seconds / 60} minutos" else "$seconds segundos"
                Text("Al continuar, la descarga del certificado público estará disponible durante $duration.")
                Button({ onEnroll(os) }, enabled = !busy && status.transport == "https") { Text(if (busy) "Preparando…" else "Continuar") }
            } else {
                Text("Escanea desde ${os.label}", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().height(260.dp).background(Color.White), contentAlignment = Alignment.Center) {
                    qr?.takeIf { session != null }?.let { Image(it.asImageBitmap(), "QR para descargar la CA pública POS Ticket Bridge mobile", Modifier.size(260.dp)) }
                        ?: if (busy || session != null) CircularProgressIndicator() else Text("Descarga finalizada", color = Color.Black)
                }
                if (session != null) {
                    val seconds = ((session.expiresAt - now) / 1000).coerceAtLeast(0)
                    Text("Disponible ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")
                    CopyHttpsValue("O abre este enlace en el dispositivo", session.url)
                    if (os == ClientOs.ANDROID) TextButton({ open(session.url) }) { Text("Descargar en este teléfono") }
                } else Button({ onEnroll(os) }, enabled = !busy && status.transport == "https") { Text("Reactivar descarga") }
                Text("Identifica la CA por el nombre «POS Ticket Bridge … mobile».")
                val instructions = when (os) {
                    ClientOs.ANDROID -> listOf(
                        "Descarga el certificado en el dispositivo que usará el POS, incluso si es este mismo teléfono.",
                        "En Ajustes, busca «Instalar certificado» y elige «Certificado de CA». La ubicación puede variar según el fabricante.",
                        "Selecciona el archivo android.cer descargado y confirma la instalación de la CA del bridge mobile.")
                    ClientOs.IOS -> listOf(
                        "Descarga y permite el perfil. Abre Ajustes → Perfil descargado e instálalo.",
                        "Ve a General → Información → Ajustes de confianza de los certificados y activa la confianza total para la CA del bridge mobile.")
                    ClientOs.WINDOWS -> listOf(
                        "Abre windows.cer y pulsa «Instalar certificado». Selecciona «Usuario actual».",
                        "Elige «Colocar todos los certificados en el siguiente almacén» y pulsa «Examinar».",
                        "Selecciona «Entidades de certificación raíz de confianza», acepta y finaliza la instalación. Confirma que corresponde a la CA del bridge mobile.",
                        "Cierra todas las ventanas del navegador y vuelve a abrirlo.")
                    ClientOs.MACOS -> listOf(
                        "Abre macos.cer e importa la CA en Acceso a Llaveros.",
                        "Abre el certificado del bridge mobile, despliega «Confiar» y selecciona «Confiar siempre» para SSL. Confirma los cambios.",
                        "Cierra y vuelve a abrir el navegador.")
                }
                Text("Instalación en ${os.label}", style = MaterialTheme.typography.titleMedium)
                instructions.forEachIndexed { index, instruction -> HttpsInstruction(index + 1, instruction) }
                if (status.transport == "https") {
                    HttpsInstruction(instructions.size + 1,
                        "Abre la prueba HTTPS en el mismo navegador y dispositivo donde usarás el POS. Debe mostrar un JSON sin advertencias de certificado. Después vuelve al POS y prueba la conexión.")
                    CopyHttpsValue("Comprueba HTTPS en el navegador del dispositivo", "${status.host}/health")
                    OutlinedButton({ open("${status.host}/health") }) { Text("Abrir prueba HTTPS") }
                }
                Text("¿Tienes problemas?", style = MaterialTheme.typography.titleSmall)
                setup.video(os)?.let { url -> TextButton({ open(url) }) { Text("Mira el video") } }
                if (setup.guide(os) != os.guide) {
                    TextButton({ open(setup.guide(os)) }) { Text("Ver guía de instalación") }
                }
                TextButton({ open(os.guide) }) { Text("Guía oficial de ${os.label}") }
                TextButton({ details = !details }) { Text("Detalles del certificado") }
                if (details) status.fingerprint?.let { CopyHttpsValue("Huella SHA-256 de la CA", it) }
                TextButton({ onStop(); step = 1 }, enabled = !busy) { Text("Cambiar sistema operativo") }
            }
            (error ?: status.error)?.let { Text(httpsError(it), color = MaterialTheme.colorScheme.error) }
            if (openError) Text("No se pudo abrir el enlace. Cópialo y ábrelo en tu navegador.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun HttpsInstruction(number: Int, instruction: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        Text(instruction, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CopyHttpsValue(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(value) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.bodySmall) }
        IconButton({ scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))); copied = true } }) {
            Icon(Icons.Default.ContentCopy, "Copiar $label")
        }
    }
    if (copied) Text("Copiado", style = MaterialTheme.typography.labelSmall)
}

private fun formatDate(value: Long) = DateFormat.getDateInstance().format(Date(value))
internal fun httpsError(code: String): String = when (code) {
    "https_select_interface" -> "Selecciona una red disponible para HTTPS."
    "https_interface_missing" -> "La red seleccionada ya no está disponible. Conéctala de nuevo o elige otra en Ajustes."
    "https_ca_expired" -> "La CA venció o la fecha del teléfono es incorrecta. Revisa la fecha o restablece HTTPS."
    "https_store_invalid", "https_decryption_failed" -> "No se pueden leer los certificados. Se conservaron los archivos; reintenta o restablece HTTPS desde Ajustes."
    "https_must_be_active" -> "Activa HTTPS antes de configurar un dispositivo."
    "local_network_permission_required" -> "Concede acceso a la red local antes de activar HTTPS."
    else -> "No se pudo actualizar HTTPS. Comprueba la red y reintenta."
}
