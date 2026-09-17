package com.luiscarodev.posticketbridge.bridge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BridgePortTransitionTest {
    @Test fun startsAndPersistsCandidateBeforeStoppingPrevious() = runBlocking {
        val events = mutableListOf<String>()
        switchBridgePort(
            previous = "old",
            candidate = "new",
            startCandidate = { events += "start:$it" },
            persist = { events += "persist" },
            stop = { events += "stop:$it" },
            publish = { events += "publish:$it" },
        )
        assertEquals(
            listOf("start:new", "persist", "stop:old", "publish:new"),
            events,
        )
    }

    @Test fun startFailureKeepsPreviousAndDoesNotPersist() {
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                switchBridgePort(
                    previous = "old",
                    candidate = "new",
                    startCandidate = { events += "start:$it"; error("occupied") },
                    persist = { events += "persist" },
                    stop = { events += "stop:$it" },
                    publish = { events += "publish:$it" },
                )
            }
        }
        assertEquals(listOf("start:new", "stop:new", "publish:old"), events)
    }

    @Test fun persistenceFailureStopsCandidateAndKeepsPrevious() {
        val events = mutableListOf<String>()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                switchBridgePort(
                    previous = "old",
                    candidate = "new",
                    startCandidate = { events += "start:$it" },
                    persist = { events += "persist"; throw IllegalArgumentException("disk") },
                    stop = { events += "stop:$it" },
                    publish = { events += "publish:$it" },
                )
            }
        }
        assertEquals(
            listOf("start:new", "persist", "stop:new", "publish:old"),
            events,
        )
    }
}
