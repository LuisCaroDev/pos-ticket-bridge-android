package com.luiscarodev.posticketbridge.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
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
    internal val portChanges = Channel<PortChangeCommand>(Channel.UNLIMITED)

    fun update(state: BridgeRuntimeState) {
        mutableState.value = state
    }

    suspend fun changePort(port: Int) {
        val command = PortChangeCommand(port)
        portChanges.send(command)
        command.result.await()
    }
}

internal data class PortChangeCommand(
    val port: Int,
    val result: CompletableDeferred<Unit> = CompletableDeferred(),
)
