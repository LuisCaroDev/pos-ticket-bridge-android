package com.luiscarodev.posticketbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedOriginsUiStateTest {
    @Test
    fun commitsPastedOriginsAsOrderedDeduplicatedChips() {
        val state = AllowedOriginsUiState.fromPersisted(listOf("https://one.example"))
            .withInput("https://two.example, HTTPS://ONE.EXAMPLE:443\nhttp://three.example:8080")
            .commitInput()

        assertEquals(
            listOf(
                "https://one.example",
                "https://two.example",
                "http://three.example:8080",
            ),
            state.origins,
        )
        assertEquals("", state.input)
        assertTrue(state.dirty)
    }

    @Test
    fun invalidInputRemainsEditableAndDoesNotCreateAChip() {
        val state = AllowedOriginsUiState.fromPersisted(emptyList())
            .withInput("https://pos.example/path")
            .commitInput()

        assertTrue(state.origins.isEmpty())
        assertEquals("https://pos.example/path", state.input)
        assertEquals(AllowedOriginsUiState.INVALID_ORIGIN_ERROR, state.errorCode)
    }

    @Test
    fun removingAndRestoringAnOriginReturnsToCleanState() {
        val initial = AllowedOriginsUiState.fromPersisted(listOf("https://pos.example"))
        val removed = initial.remove("https://pos.example")
        val restored = removed.withInput("https://pos.example").commitInput()

        assertTrue(removed.dirty)
        assertFalse(restored.dirty)
    }
}
