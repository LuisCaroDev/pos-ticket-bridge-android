package com.luiscarodev.posticketbridge.bridge.https

import com.luiscarodev.posticketbridge.BuildConfig
import kotlinx.serialization.Serializable
import java.net.Inet4Address
import java.net.NetworkInterface

val ENROLLMENT_PORT: Int = BuildConfig.ENROLLMENT_PORT
const val ENROLLMENT_DURATION_MS = 600_000L

@Serializable
data class HttpsNetwork(val name: String, val address: String, val prefix: Int = 24)

@Serializable
data class HttpsRecord(
    val version: Int = 1,
    val enabled: Boolean = false,
    val selection: HttpsNetwork? = null,
    val material: CertificateMaterial? = null,
)

@Serializable
data class CertificateMaterial(val ca: String, val caKey: String, val certificate: String, val key: String)

enum class ClientOs(val label: String, val filename: String, val guide: String) {
    ANDROID("Android", "android.cer", "https://support.google.com/pixelphone/answer/2844832?hl=es"),
    IOS("iPhone / iPad", "ios.mobileconfig", "https://support.apple.com/es-es/102390"),
    WINDOWS("Windows", "windows.cer", "https://learn.microsoft.com/es-es/windows-hardware/drivers/install/trusted-root-certification-authorities-certificate-store"),
    MACOS("macOS", "macos.cer", "https://support.apple.com/es-es/guide/keychain-access/kyca11871/mac"),
}

data class EnrollmentSession(val url: String, val expiresAt: Long, val os: ClientOs)

data class HttpsStatus(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val selection: HttpsNetwork? = null,
    val networks: List<HttpsNetwork> = emptyList(),
    val transport: String = "stopped",
    val host: String = "",
    val fingerprint: String? = null,
    val expiresAt: Long? = null,
    val caExpiresAt: Long? = null,
    val enrollment: EnrollmentSession? = null,
    val error: String? = null,
)

fun localHttpsNetworks(): List<HttpsNetwork> =
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback && Regex("^(wlan|ap|swlan|eth|rndis|bt-pan).*", RegexOption.IGNORE_CASE).matches(it.name) }
        .flatMap { network -> network.interfaceAddresses.mapNotNull { entry ->
            val ip = entry.address as? Inet4Address ?: return@mapNotNull null
            if (!ip.isSiteLocalAddress) null
            else HttpsNetwork(network.name, ip.hostAddress!!, entry.networkPrefixLength.toInt())
        } }

fun sameSubnet(remote: String, network: HttpsNetwork): Boolean {
    fun ipv4(value: String): Long? {
        val parts = value.removePrefix("::ffff:").split('.')
        if (parts.size != 4) return null
        return parts.fold(0L) { result, part ->
            val number = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            (result shl 8) or number.toLong()
        }
    }
    if (network.prefix !in 1..32) return false
    val address = ipv4(remote) ?: return false
    val local = ipv4(network.address) ?: return false
    val mask = (0xffffffffL shl (32 - network.prefix)) and 0xffffffffL
    return address and mask == local and mask
}
