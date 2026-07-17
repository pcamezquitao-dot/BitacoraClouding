package com.cactus.bitacora.ui.bitacora

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturePanelState(
    val expandedTarget: CaptureTarget? = null,
    val mode: CaptureMode = CaptureMode.CLOSED
)

class BitacoraDiariaViewModel : ViewModel() {
    private val _captureState = MutableStateFlow(CapturePanelState())
    val captureState: StateFlow<CapturePanelState> = _captureState.asStateFlow()

    fun open(target: CaptureTarget, mode: CaptureMode) {
        require(mode != CaptureMode.CLOSED)
        _captureState.value = CapturePanelState(target, mode)
    }

    fun updateMode(mode: CaptureMode) {
        val target = _captureState.value.expandedTarget
        _captureState.value = if (mode == CaptureMode.CLOSED || target == null) {
            CapturePanelState()
        } else {
            CapturePanelState(target, mode)
        }
    }

    fun close() {
        _captureState.value = CapturePanelState()
    }
}
