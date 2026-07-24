package com.cactus.bitacora.ui.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEvidencePolicyTest {
    @Test
    fun emptyTextCannotBeSaved() {
        assertFalse(canSaveTextEvidence(" \n\t "))
    }

    @Test
    fun validTextIsTrimmedAndCanBeSaved() {
        assertTrue(canSaveTextEvidence("  novedad offline  "))
        assertEquals("novedad offline", normalizedTextEvidence("  novedad offline  "))
    }

    @Test
    fun multipleIndependentTextsRemainValid() {
        val values = listOf("primera", "segunda", "tercera")
        assertTrue(values.all(::canSaveTextEvidence))
        assertEquals(3, values.map(::normalizedTextEvidence).distinct().size)
    }
}
