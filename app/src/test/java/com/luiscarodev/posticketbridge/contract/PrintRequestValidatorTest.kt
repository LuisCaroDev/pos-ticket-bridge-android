package com.luiscarodev.posticketbridge.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintRequestValidatorTest {
    @Test
    fun acceptsEveryDesktopV1Block() {
        val fixture = checkNotNull(javaClass.classLoader)
            .getResource("print-job-v1-all-blocks.fixture.json")
            .readText()

        val result = PrintRequestValidator.validate(fixture)
        assertTrue(result is PrintRequestValidation.Valid)
        assertEquals("caja", (result as PrintRequestValidation.Valid).printerId)
        assertEquals(9, result.request.job.blocks.size)
    }

    @Test
    fun acceptsAllNativeScalesAndRejectsValuesOutsideOneToEight() {
        for (scale in 1..8) {
            assertTrue(
                PrintRequestValidator.validate(request(textFields = "\"width\":$scale"))
                    is PrintRequestValidation.Valid,
            )
        }
        for (scale in listOf(0, 9)) {
            assertEquals(
                PrintRequestValidation.Invalid,
                PrintRequestValidator.validate(request(textFields = "\"width\":$scale")),
            )
        }
    }

    @Test
    fun preservesDesktopStrictTextAndLenientOtherBlocks() {
        assertEquals(
            PrintRequestValidation.Invalid,
            PrintRequestValidator.validate(request(textFields = "\"unexpected\":true")),
        )
        assertTrue(
            PrintRequestValidator.validate(
                """{"printerId":"caja","job":{"version":1,"blocks":[{"type":"image","url":"x","unexpected":true}]}}""",
            ) is PrintRequestValidation.Valid,
        )
    }

    @Test
    fun rejectsMalformedRequestsAndUnsupportedBlocks() {
        val invalid = listOf(
            "not-json",
            "{}",
            """{"printerId":"","job":{"version":1,"blocks":[]}}""",
            """{"printerId":"caja","job":{"version":2,"blocks":[]}}""",
            """{"printerId":"caja","job":{"version":1,"widthMm":76,"blocks":[]}}""",
            """{"printerId":"caja","job":{"version":1,"blocks":[{"type":"audio"}]}}""",
        )
        invalid.forEach { body ->
            assertEquals(PrintRequestValidation.Invalid, PrintRequestValidator.validate(body))
        }
    }

    private fun request(textFields: String): String =
        """{"printerId":"caja","job":{"version":1,"blocks":[{"type":"text","content":"Ticket",$textFields}]}}"""
}
