package com.luiscarodev.posticketbridge.bridge.https

import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.util.Base64

/** A separate, temporary HTTP listener; it never exposes tokens, keys or print routes. */
class HttpsEnrollmentServer(private val port: Int = ENROLLMENT_PORT) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    fun start(network: HttpsNetwork, ca: String, expiresAt: Long) {
        stop()
        val cert = Base64.getDecoder().decode(ca)
        val profile = HttpsCertificates.iosProfile(ca)
        val next = embeddedServer(CIO, configure = {
            connectionIdleTimeoutSeconds = 10
            connector { host = network.address; port = this@HttpsEnrollmentServer.port }
        }) {
            intercept(ApplicationCallPipeline.Call) {
                call.response.headers.append("Cache-Control", "no-store")
                call.response.headers.append("X-Content-Type-Options", "nosniff")
                if (System.currentTimeMillis() >= expiresAt || !sameSubnet(call.request.local.remoteAddress, network)) {
                    call.respond(HttpStatusCode.Forbidden)
                } else if (call.request.httpMethod !in listOf(HttpMethod.Get, HttpMethod.Head)) {
                    call.response.headers.append("Allow", "GET, HEAD")
                    call.respond(HttpStatusCode.MethodNotAllowed)
                } else {
                    val os = ClientOs.entries.find { call.request.uri == "/setup/${it.filename}" }
                    if (os == null) call.respond(HttpStatusCode.NotFound)
                    else {
                        val bytes = if (os == ClientOs.IOS) profile else cert
                        call.response.headers.append("Content-Disposition", "attachment; filename=\"${os.filename}\"")
                        val type = ContentType.parse(if (os == ClientOs.IOS) "application/x-apple-aspen-config" else "application/pkix-cert")
                        if (call.request.httpMethod == HttpMethod.Head) call.respond(object : OutgoingContent.NoContent() {
                            override val contentType = type
                            override val contentLength = bytes.size.toLong()
                            override val status = HttpStatusCode.OK
                        }) else call.respondBytes(bytes, type)
                    }
                }
                finish()
            }
        }
        try { next.start(false); server = next }
        catch (error: Exception) { next.stop(0, 1000); throw error }
    }
    fun stop() { server?.stop(0, 1000); server = null }
}
