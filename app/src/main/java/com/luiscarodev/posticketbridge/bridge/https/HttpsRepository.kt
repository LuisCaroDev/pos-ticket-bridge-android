package com.luiscarodev.posticketbridge.bridge.https

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HttpsAction {
    data class Apply(val enabled: Boolean, val network: HttpsNetwork?) : HttpsAction
    data class Enroll(val os: ClientOs) : HttpsAction
    data object StopEnrollment : HttpsAction
    data object Reset : HttpsAction
    data object Retry : HttpsAction
}
data class HttpsCommand(val action: HttpsAction, val result: CompletableDeferred<Unit> = CompletableDeferred())

class HttpsRepository {
    private val mutableState = MutableStateFlow(HttpsStatus())
    val state = mutableState.asStateFlow()
    internal val commands = Channel<HttpsCommand>(Channel.UNLIMITED)
    @Volatile var clientCa: String? = null
        internal set
    internal fun update(status: HttpsStatus) { mutableState.value = status }
    suspend fun execute(action: HttpsAction) {
        val command = HttpsCommand(action)
        commands.send(command)
        command.result.await()
    }
    fun stopEnrollment() { commands.trySend(HttpsCommand(HttpsAction.StopEnrollment)) }
}
