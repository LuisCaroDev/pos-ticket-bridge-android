package com.luiscarodev.posticketbridge.bridge

internal suspend fun <T> switchBridgePort(
    previous: T,
    candidate: T,
    startCandidate: (T) -> Unit,
    persist: suspend () -> Unit,
    stop: (T) -> Unit,
    publish: (T) -> Unit,
) {
    try {
        startCandidate(candidate)
    } catch (error: Exception) {
        runCatching { stop(candidate) }
        publish(previous)
        throw error
    }
    try {
        persist()
    } catch (error: Exception) {
        runCatching { stop(candidate) }
        publish(previous)
        throw error
    }
    stop(previous)
    publish(candidate)
}
