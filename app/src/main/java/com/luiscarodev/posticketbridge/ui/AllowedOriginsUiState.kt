package com.luiscarodev.posticketbridge.ui

import com.luiscarodev.posticketbridge.domain.AllowedOrigin
import java.io.Serializable

data class AllowedOriginsUiState(
    val initialized: Boolean = false,
    val persistedOrigins: List<String> = emptyList(),
    val origins: List<String> = emptyList(),
    val input: String = "",
    val errorCode: String? = null,
) : Serializable {
    val dirty: Boolean get() = origins != persistedOrigins || input.isNotBlank()

    fun withInput(value: String): AllowedOriginsUiState = copy(
        input = value,
        errorCode = null,
    )

    fun commitInput(): AllowedOriginsUiState {
        val candidates = input
            .split(ORIGIN_SEPARATORS)
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (candidates.isEmpty()) return copy(input = "", errorCode = null)

        val normalized = candidates.map { candidate ->
            AllowedOrigin.normalizeOrNull(candidate)
                ?: return copy(errorCode = INVALID_ORIGIN_ERROR)
        }
        return copy(
            origins = (origins + normalized).distinct(),
            input = "",
            errorCode = null,
        )
    }

    fun remove(origin: String): AllowedOriginsUiState = copy(
        origins = origins.filterNot { it == origin },
        errorCode = null,
    )

    companion object {
        const val INVALID_ORIGIN_ERROR = "invalid_origin"
        private val ORIGIN_SEPARATORS = Regex("[,;\\r\\n]+")

        fun fromPersisted(origins: List<String>): AllowedOriginsUiState = AllowedOriginsUiState(
            initialized = true,
            persistedOrigins = origins,
            origins = origins,
        )
    }
}
