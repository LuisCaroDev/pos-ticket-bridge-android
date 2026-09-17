package com.luiscarodev.posticketbridge.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionKind {
    WIFI,
    ETHERNET,
    HOTSPOT,
    LOCAL,
}

data class ConnectionUrl(
    val value: String,
    val kind: ConnectionKind,
)

data class ConnectionUrls(
    val primary: ConnectionUrl,
    val alternatives: List<ConnectionUrl>,
)

class ConnectionUrlRepository(context: Context, initialPort: Int) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val networkSnapshots = ConcurrentHashMap<Network, NetworkSnapshot>()
    @Volatile private var port = initialPort
    private val mutableUrls = MutableStateFlow(prioritizeConnectionUrls(emptyList(), port))
    val urls: StateFlow<ConnectionUrls> = mutableUrls.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = capture(network)
        override fun onLost(network: Network) {
            networkSnapshots.remove(network)
            refresh()
        }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            networkSnapshots.compute(network) { _, current ->
                NetworkSnapshot(caps, current?.properties)
            }
            refresh()
        }
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            networkSnapshots.compute(network) { _, current ->
                NetworkSnapshot(current?.capabilities, properties)
            }
            refresh()
        }
    }

    init {
        connectivityManager.activeNetwork?.let(::capture)
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),
                callback,
            )
        }
        refresh()
    }

    fun refresh() {
        mutableUrls.value = resolve()
    }

    fun updatePort(port: Int) {
        if (this.port == port) return
        this.port = port
        refresh()
    }

    private fun resolve(): ConnectionUrls {
        val activeNetwork = connectivityManager.activeNetwork
        val candidates = buildList {
            networkSnapshots.forEach { (network, snapshot) ->
                val capabilities = snapshot.capabilities ?: return@forEach
                val kind = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionKind.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionKind.ETHERNET
                    else -> return@forEach
                }
                snapshot.properties?.linkAddresses.orEmpty()
                    .map { it.address }
                    .filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                    .forEach { address ->
                        add(AddressCandidate(address.hostAddress.orEmpty(), kind, network == activeNetwork))
                    }
            }

            hotspotAddresses().forEach { address ->
                add(AddressCandidate(address, ConnectionKind.HOTSPOT, false))
            }
        }
        return prioritizeConnectionUrls(candidates, port)
    }

    private fun capture(network: Network) {
        networkSnapshots[network] = NetworkSnapshot(
            capabilities = connectivityManager.getNetworkCapabilities(network),
            properties = connectivityManager.getLinkProperties(network),
        )
        refresh()
    }

    private fun hotspotAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && HOTSPOT_INTERFACE.matches(it.name) }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    companion object {
        private val HOTSPOT_INTERFACE = Regex("^(wlan|ap|swlan|eth|rndis|bt-pan).*", RegexOption.IGNORE_CASE)
    }

    private data class NetworkSnapshot(
        val capabilities: NetworkCapabilities?,
        val properties: LinkProperties?,
    )
}

data class AddressCandidate(
    val address: String,
    val kind: ConnectionKind,
    val active: Boolean,
)

fun prioritizeConnectionUrls(candidates: List<AddressCandidate>, port: Int): ConnectionUrls {
    val ordered = candidates
        .filter { it.address.isNotBlank() }
        .distinctBy { it.address }
        .sortedWith(
            compareByDescending<AddressCandidate> { it.active }
                .thenBy { kindPriority(it.kind) },
        )
        .map { ConnectionUrl("http://${it.address}:$port", it.kind) }
    val local = ConnectionUrl("http://127.0.0.1:$port", ConnectionKind.LOCAL)
    return if (ordered.isEmpty()) {
        ConnectionUrls(primary = local, alternatives = emptyList())
    } else {
        ConnectionUrls(primary = ordered.first(), alternatives = ordered.drop(1) + local)
    }
}

private fun kindPriority(kind: ConnectionKind): Int = when (kind) {
    ConnectionKind.WIFI -> 0
    ConnectionKind.ETHERNET -> 1
    ConnectionKind.HOTSPOT -> 2
    ConnectionKind.LOCAL -> 3
}
