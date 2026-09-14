package com.luiscarodev.posticketbridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luiscarodev.posticketbridge.BridgeApplication
import com.luiscarodev.posticketbridge.bridge.BridgeForegroundService
import com.luiscarodev.posticketbridge.bridge.https.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HttpsDraft(
    val initialized: Boolean = false,
    val enabled: Boolean = false,
    val name: String? = null,
    val address: String? = null,
    val edited: Boolean = false,
) : java.io.Serializable

class HttpsViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as BridgeApplication
    val status = app.httpsRepository.state
    private val mutableDraft = MutableStateFlow(saved.get<HttpsDraft>("https-draft") ?: HttpsDraft())
    val draft = mutableDraft.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    init {
        viewModelScope.launch {
            status.collect { value ->
                if (value.loaded && !mutableDraft.value.edited) syncDraft()
            }
        }
    }

    private fun setDraft(value: HttpsDraft) { mutableDraft.value = value; saved["https-draft"] = value }
    private fun syncDraft() {
        val value = status.value
        setDraft(HttpsDraft(true, value.enabled, value.selection?.name, value.selection?.address))
    }
    fun enable(enabled: Boolean) {
        val current = draft.value
        val selection = status.value.networks.singleOrNull()
        setDraft(current.copy(enabled = enabled, edited = true,
            name = current.name ?: selection?.name, address = current.address ?: selection?.address))
        mutableError.value = null
    }
    fun select(network: HttpsNetwork) {
        setDraft(draft.value.copy(name = network.name, address = network.address, edited = true))
        mutableError.value = null
    }
    fun save() {
        val current = draft.value
        val network = status.value.networks.find { it.name == current.name && it.address == current.address }
            ?: status.value.selection?.takeIf { !current.enabled }
        if (current.enabled && network == null) { mutableError.value = "https_select_interface"; return }
        perform(HttpsAction.Apply(current.enabled, network)) { syncDraft() }
    }
    fun reset() = perform(HttpsAction.Reset) { syncDraft() }
    fun retry() = perform(HttpsAction.Retry)
    fun enroll(os: ClientOs) = perform(HttpsAction.Enroll(os))
    fun stopEnrollment() { app.httpsRepository.stopEnrollment() }

    private fun perform(action: HttpsAction, success: () -> Unit = {}) {
        if (mutableBusy.value) return
        if (!BridgeForegroundService.hasRequiredLocalNetworkPermission(app)) {
            mutableError.value = "local_network_permission_required"
            return
        }
        mutableBusy.value = true
        mutableError.value = null
        viewModelScope.launch {
            try {
                BridgeForegroundService.start(app)
                app.httpsRepository.execute(action)
                success()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                mutableError.value = error.message ?: "https_operation_failed"
            } finally { mutableBusy.value = false }
        }
    }
}
