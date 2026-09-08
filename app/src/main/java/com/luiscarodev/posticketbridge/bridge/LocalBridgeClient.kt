package com.luiscarodev.posticketbridge.bridge

import com.luiscarodev.posticketbridge.data.BridgeSettingsRepository
import com.luiscarodev.posticketbridge.contract.BridgeJson
import com.luiscarodev.posticketbridge.contract.ErrorResponse
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalBridgeClient(private val settings: BridgeSettingsRepository) {
    suspend fun testPrinter(printerId: String) = withContext(Dispatchers.IO) {
        val config = settings.getOrCreate()
        val connection = URL("http://127.0.0.1:${config.port}/test/$printerId")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 2_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("x-agent-token", config.token)
            connection.setRequestProperty("content-type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray()) }
            if (connection.responseCode !in 200..299) {
                val code = runCatching {
                    BridgeJson.decodeFromString<ErrorResponse>(
                        connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty(),
                    ).error.code
                }.getOrDefault("printer_test_failed")
                error(code)
            }
        } finally { connection.disconnect() }
    }
}
