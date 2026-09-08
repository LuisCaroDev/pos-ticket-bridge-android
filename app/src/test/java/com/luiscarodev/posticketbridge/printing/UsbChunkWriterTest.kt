package com.luiscarodev.posticketbridge.printing

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbChunkWriterTest {
    @Test
    fun writesEveryByteInBoundedChunks() {
        val calls = mutableListOf<Pair<Int, Int>>()
        writeUsbChunks(ByteArray(10), chunkSize = 4) { offset, length ->
            calls += offset to length
            length
        }
        assertEquals(listOf(0 to 4, 4 to 4, 8 to 2), calls)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsFailedUsbWrite() {
        writeUsbChunks(ByteArray(1)) { _, _ -> 0 }
    }
}
