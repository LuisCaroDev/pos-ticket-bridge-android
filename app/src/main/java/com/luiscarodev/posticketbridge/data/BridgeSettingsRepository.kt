package com.luiscarodev.posticketbridge.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luiscarodev.posticketbridge.domain.AllowedOrigin
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

const val BRIDGE_PORT = 9977

data class BridgeSettings(
    val token: String,
    val allowedOrigins: List<String>,
    val port: Int = BRIDGE_PORT,
)

private val Context.bridgeDataStore by preferencesDataStore(name = "bridge_settings")

class BridgeSettingsRepository(private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("token")
        val allowedOrigins = stringPreferencesKey("allowed_origins")
    }

    val settings: Flow<BridgeSettings> = context.bridgeDataStore.data.map(::toSettings)

    suspend fun getOrCreate(): BridgeSettings {
        context.bridgeDataStore.edit { preferences ->
            if (preferences[Keys.token].isNullOrBlank()) {
                preferences[Keys.token] = generateToken()
            }
        }
        return settings.first()
    }

    suspend fun saveAllowedOrigins(origins: List<String>): BridgeSettings {
        val normalized = origins.map { origin ->
            AllowedOrigin.normalizeOrNull(origin) ?: error("invalid_origin")
        }.distinct()
        context.bridgeDataStore.edit { preferences ->
            preferences[Keys.allowedOrigins] = normalized.joinToString("\n")
            if (preferences[Keys.token].isNullOrBlank()) {
                preferences[Keys.token] = generateToken()
            }
        }
        return settings.first()
    }

    private fun toSettings(preferences: Preferences): BridgeSettings = BridgeSettings(
        token = preferences[Keys.token].orEmpty(),
        allowedOrigins = normalizeOrigins(
            preferences[Keys.allowedOrigins].orEmpty().lineSequence().toList(),
        ),
    )

    companion object {
        private val secureRandom = SecureRandom()

        fun normalizeOrigins(origins: List<String>): List<String> = origins
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

        fun generateToken(): String {
            val bytes = ByteArray(24)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
