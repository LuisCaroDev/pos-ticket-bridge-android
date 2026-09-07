package com.luiscarodev.posticketbridge.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BridgeRuntimeState {
    data object Stopped : BridgeRuntimeState
    data object Starting : BridgeRuntimeState
    data class Running(val hosts: List<String>, val port: Int) : BridgeRuntimeState
    data class Failed(val reason: String) : BridgeRuntimeState
}

class BridgeRuntimeRepository {
    private val mutableState = MutableStateFlow<BridgeRuntimeState>(BridgeRuntimeState.Stopped)
    val state: StateFlow<BridgeRuntimeState> = mutableState.asStateFlow()

    fun update(state: BridgeRuntimeState) {
        mutableState.value = state
    }
}
