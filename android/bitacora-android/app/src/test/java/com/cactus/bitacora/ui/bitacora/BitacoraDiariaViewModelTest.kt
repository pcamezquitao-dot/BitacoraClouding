package com.cactus.bitacora.ui.bitacora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitacoraDiariaViewModelTest {
    @Test
    fun openingAnotherTargetClosesPreviousPanel() {
        val viewModel = BitacoraDiariaViewModel()
        viewModel.open(CaptureTarget.EMPLEADO, CaptureMode.QR_SCANNING)
        viewModel.open(CaptureTarget.AREA, CaptureMode.MANUAL_ENTRY)

        assertEquals(CaptureTarget.AREA, viewModel.captureState.value.expandedTarget)
        assertEquals(CaptureMode.MANUAL_ENTRY, viewModel.captureState.value.mode)
    }

    @Test
    fun closeResetsCapturePanel() {
        val viewModel = BitacoraDiariaViewModel()
        viewModel.open(CaptureTarget.SUPERVISOR, CaptureMode.PHOTO_CAPTURE)
        viewModel.close()

        assertNull(viewModel.captureState.value.expandedTarget)
        assertEquals(CaptureMode.CLOSED, viewModel.captureState.value.mode)
    }
}
