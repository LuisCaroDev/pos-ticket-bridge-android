package com.luiscarodev.posticketbridge.bridge.https

import com.luiscarodev.posticketbridge.bridge.BridgeHttpServer

/** Called serially on the foreground service's IO dispatcher. No UI owns sockets. */
class LocalHttpsController(
    private val store: HttpsStore,
    private val repository: HttpsRepository,
    private val factory: (HttpsRecord) -> BridgeHttpServer,
    private val port: Int,
    private val networks: () -> List<HttpsNetwork> = ::localHttpsNetworks,
    private val enrollment: HttpsEnrollmentServer = HttpsEnrollmentServer(),
    private val enrollmentPort: Int = ENROLLMENT_PORT,
    private val setup: HttpsSetupConfiguration = HttpsSetupConfiguration.bundled,
) {
    private var record: HttpsRecord? = null
    private var server: BridgeHttpServer? = null
    private var session: EnrollmentSession? = null
    private var lastFailure: String? = null

    fun restart() = guarded {
        val previous = record ?: store.read()
        if (record == null) record = previous
        val next = prepare(previous)
        transition(next, force = true)
    }

    fun execute(action: HttpsAction) = guarded {
        when (action) {
            is HttpsAction.Apply -> {
                val current = record ?: store.read()
                if (action.enabled) require(networks().any { it == action.network }) { "https_select_interface" }
                transition(prepare(current.copy(enabled = action.enabled, selection = action.network)))
            }
            is HttpsAction.Enroll -> {
                val current = record ?: error("https_must_be_active")
                check(current.enabled && server != null) { "https_must_be_active" }
                val network = resolve(current.selection)
                check(network == current.selection) { "https_interface_missing" }
                stopEnrollment()
                val expiresAt = System.currentTimeMillis() + setup.durationMs
                enrollment.start(network, current.material!!.ca, expiresAt)
                session = EnrollmentSession("http://${network.address}:$enrollmentPort/setup/${action.os.filename}", expiresAt, action.os)
                publish()
            }
            HttpsAction.StopEnrollment -> { stopEnrollment(); publish() }
            HttpsAction.Reset -> transition(HttpsRecord(), force = true)
            HttpsAction.Retry -> restart()
        }
    }

    fun reconcile() = guarded {
        if (session?.let { System.currentTimeMillis() >= it.expiresAt } == true) stopEnrollment()
        val current = record ?: return@guarded
        if (current.enabled || server == null) {
            try { transition(prepare(current)) }
            catch (error: Exception) {
                stopEnrollment()
                if (!valid(current)) { server?.stop(); server = null }
                throw error
            }
        }
        publish()
    }

    private fun prepare(current: HttpsRecord): HttpsRecord {
        if (!current.enabled) return current
        val network = resolve(current.selection)
        return current.copy(selection = network, material = HttpsCertificates.prepare(current.material, network.address))
    }

    private fun resolve(selection: HttpsNetwork?): HttpsNetwork {
        requireNotNull(selection) { "https_select_interface" }
        val candidates = networks().filter { it.name == selection.name }
        return candidates.find { it.address == selection.address } ?: candidates.singleOrNull() ?: error("https_interface_missing")
    }

    private fun valid(value: HttpsRecord): Boolean = !value.enabled || runCatching {
        val cert = HttpsCertificates.certificate(value.material!!.certificate)
        cert.checkValidity()
        HttpsCertificates.certificate(value.material.ca).checkValidity()
        networks().any { it.name == value.selection?.name && it.address == value.selection.address }
    }.getOrDefault(false)

    private fun transition(next: HttpsRecord, force: Boolean = false) {
        if (!force && next == record && server != null) return
        val previous = record
        stopEnrollment()
        server?.stop()
        server = null
        try {
            val candidate = factory(next)
            try { candidate.start() } catch (error: Exception) { candidate.stop(); throw error }
            server = candidate
            if (next != previous) store.write(next)
            record = next
        } catch (error: Exception) {
            server?.stop(); server = null
            if (previous != null && valid(previous)) {
                runCatching {
                    val restored = factory(previous)
                    try { restored.start(); server = restored }
                    catch (failure: Exception) { restored.stop(); throw failure }
                }
            }
            throw error
        }
        publish()
    }

    private fun stopEnrollment() { enrollment.stop(); session = null }
    fun shutdown() { stopEnrollment(); server?.stop(); server = null; publish() }
    fun publishCurrent() { publish() }

    private fun guarded(block: () -> Unit) {
        try { block() } catch (error: Exception) {
            lastFailure = if (generateSequence<Throwable>(error) { it.cause }.any { it is java.net.BindException })
                "bridge_port_in_use"
            else error.message?.takeIf { it.startsWith("https_") } ?: "https_operation_failed"
            publish(lastFailure)
            throw error
        }
    }

    private fun publish(error: String? = null) {
        if (server != null && error == null) lastFailure = null
        val current = record
        val material = current?.material
        val transport = if (server == null) "stopped" else if (current?.enabled == true) "https" else "http"
        val available = runCatching(networks).getOrDefault(emptyList())
        val address = if (current?.enabled == true) current.selection?.address else available.firstOrNull()?.address ?: "127.0.0.1"
        repository.clientCa = if (transport == "https") material?.ca else null
        repository.update(HttpsStatus(
            loaded = true, enabled = current?.enabled == true, configured = material != null,
            selection = current?.selection, networks = available, transport = transport,
            host = if (transport == "stopped") "" else "$transport://$address:$port",
            fingerprint = material?.let { runCatching { HttpsCertificates.fingerprint(it.ca) }.getOrNull() },
            expiresAt = material?.let { runCatching { HttpsCertificates.certificate(it.certificate).notAfter.time }.getOrNull() },
            caExpiresAt = material?.let { runCatching { HttpsCertificates.certificate(it.ca).notAfter.time }.getOrNull() },
            enrollment = session, error = error ?: lastFailure,
        ))
    }
}
