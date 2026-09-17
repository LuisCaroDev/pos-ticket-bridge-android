package com.luiscarodev.posticketbridge.ui

data class PortUiState(
    val initialized: Boolean = false,
    val persistedPort: Int = 0,
    val input: String = "",
    val reservedPort: Int = 0,
) : java.io.Serializable {
    val parsedPort: Int?
        get() = input.toIntOrNull()

    val errorCode: String?
        get() = when {
            input.isBlank() -> "port_required"
            parsedPort == null || parsedPort !in 1..65535 -> "invalid_port"
            parsedPort == reservedPort -> "https_reserved_port"
            else -> null
        }

    val dirty: Boolean
        get() = initialized && parsedPort != null && parsedPort != persistedPort

    val canSave: Boolean
        get() = dirty && errorCode == null

    fun withInput(value: String): PortUiState = copy(input = value)

    companion object {
        fun fromPersisted(port: Int, reservedPort: Int) = PortUiState(
            initialized = true,
            persistedPort = port,
            input = port.toString(),
            reservedPort = reservedPort,
        )
    }
}
