package com.luiscarodev.posticketbridge.bridge

import java.net.Inet4Address
import java.net.NetworkInterface

fun suggestedHosts(port: Int): List<String> {
    val addresses = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses?.toList().orEmpty() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .map { "http://${it.hostAddress}:$port" }
    }.getOrDefault(emptyList())
    return (addresses + "http://127.0.0.1:$port").distinct()
}
