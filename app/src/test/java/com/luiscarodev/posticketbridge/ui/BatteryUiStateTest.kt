package com.luiscarodev.posticketbridge.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryUiStateTest {
    @Test
    fun unrestrictedStateHasNoWarnings() {
        assertFalse(BatteryUiState.Unrestricted.hasWarnings)
    }

    @Test
    fun powerSaverProducesWarning() {
        assertTrue(BatteryUiState(isPowerSaveMode = true).hasWarnings)
    }

    @Test
    fun batteryOptimizationProducesWarning() {
        assertTrue(BatteryUiState(isIgnoringBatteryOptimizations = false).hasWarnings)
    }
}
