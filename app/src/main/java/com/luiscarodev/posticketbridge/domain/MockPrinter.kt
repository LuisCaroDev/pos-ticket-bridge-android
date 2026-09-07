package com.luiscarodev.posticketbridge.domain

import kotlinx.serialization.Serializable

@Serializable
data class MockPrinter(
    val id: String,
    val nombre: String,
    val tipo: String,
)

object MockPrinterCatalog {
    val printers = listOf(
        MockPrinter(id = "caja", nombre = "Caja", tipo = "network"),
        MockPrinter(id = "cocina", nombre = "Cocina", tipo = "network"),
    )

    fun contains(id: String): Boolean = printers.any { it.id == id }
}
