package com.cactus.bitacora.biometric.technical

import androidx.camera.core.CameraSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceEnrollmentCameraTest {
    @Test
    fun alternatesBetweenFrontAndBackCamera() {
        assertEquals(
            CameraSelector.LENS_FACING_BACK,
            nextEnrollmentCameraLens(CameraSelector.LENS_FACING_FRONT)
        )
        assertEquals(
            CameraSelector.LENS_FACING_FRONT,
            nextEnrollmentCameraLens(CameraSelector.LENS_FACING_BACK)
        )
    }
}
