package com.cactus.bitacora.biometric.technical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceTechnicalMathTest {
    @Test
    fun acceptsCenteredFaceWithEnoughSize() {
        assertTrue(FaceTechnicalMath.quality(200, 150, 600, 550, 800, 800).accepted)
    }

    @Test
    fun rejectsSmallOrOffCenterFace() {
        assertFalse(FaceTechnicalMath.quality(360, 360, 440, 440, 800, 800).accepted)
        assertFalse(FaceTechnicalMath.quality(0, 200, 300, 500, 800, 800).accepted)
    }

    @Test
    fun identicalEmbeddingsHavePerfectSimilarityAndZeroDistance() {
        val embedding = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, FaceTechnicalMath.l2Distance(embedding, embedding), 0.0001f)
        assertEquals(1f, FaceTechnicalMath.cosineSimilarity(embedding, embedding), 0.0001f)
    }
}
