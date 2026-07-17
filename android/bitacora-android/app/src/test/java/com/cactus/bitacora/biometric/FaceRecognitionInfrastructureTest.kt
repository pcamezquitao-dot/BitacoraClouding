package com.cactus.bitacora.biometric

import com.cactus.bitacora.ui.bitacora.BitacoraDiariaViewModel
import com.cactus.bitacora.ui.bitacora.CaptureMode
import com.cactus.bitacora.ui.bitacora.CaptureTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceRecognitionInfrastructureTest {
    @Test
    fun configurableThresholdIsNormalized() {
        assertTrue(FaceRecognitionConfig.MATCH_THRESHOLD in 0f..1f)
    }

    @Test
    fun employeeAndSupervisorRemainIndependentTargets() {
        val viewModel = BitacoraDiariaViewModel()
        viewModel.open(CaptureTarget.EMPLEADO, CaptureMode.FACE_DETECTING)
        assertEquals(CaptureTarget.EMPLEADO, viewModel.captureState.value.expandedTarget)

        viewModel.open(CaptureTarget.SUPERVISOR, CaptureMode.FACE_MATCHING)
        assertEquals(CaptureTarget.SUPERVISOR, viewModel.captureState.value.expandedTarget)
    }

    @Test
    fun facialMatchRequiresConfirmationState() {
        val viewModel = BitacoraDiariaViewModel()
        viewModel.open(CaptureTarget.EMPLEADO, CaptureMode.AWAITING_FACE_CONFIRMATION)

        assertEquals(
            CaptureMode.AWAITING_FACE_CONFIRMATION,
            viewModel.captureState.value.mode
        )
    }
}
