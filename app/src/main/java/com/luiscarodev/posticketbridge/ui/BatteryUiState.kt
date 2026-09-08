package com.luiscarodev.posticketbridge.ui

data class BatteryUiState(
    val isPowerSaveMode: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = true,
) {
    val hasWarnings: Boolean
        get() = isPowerSaveMode || !isIgnoringBatteryOptimizations

    companion object {
        val Unrestricted = BatteryUiState()
    }
}
