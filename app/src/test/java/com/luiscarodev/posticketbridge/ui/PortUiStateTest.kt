package com.luiscarodev.posticketbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortUiStateTest {
    @Test fun acceptsDesktopPortRangeAndDetectsChanges() {
        val initial = PortUiState.fromPersisted(9977, 9978)
        assertFalse(initial.dirty)
        assertFalse(initial.canSave)

        val changed = initial.withInput("12000")
        assertNull(changed.errorCode)
        assertTrue(changed.dirty)
        assertTrue(changed.canSave)
        assertEquals(12000, changed.parsedPort)
    }

    @Test fun rejectsMissingOutOfRangeAndEnrollmentPorts() {
        val initial = PortUiState.fromPersisted(9977, 9978)
        assertEquals("port_required", initial.withInput("").errorCode)
        assertEquals("invalid_port", initial.withInput("0").errorCode)
        assertEquals("invalid_port", initial.withInput("65536").errorCode)
        assertEquals("invalid_port", initial.withInput("abc").errorCode)
        assertEquals("https_reserved_port", initial.withInput("9978").errorCode)
    }
}
