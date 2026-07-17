package com.cactus.bitacora.ui.bitacora

enum class CaptureMode {
    CLOSED,
    QR_SCANNING,
    MANUAL_ENTRY,
    PHOTO_CAPTURE,
    VALIDATING,
    VALIDATED,
    ERROR
}

enum class CaptureTarget {
    EMPLEADO,
    SUPERVISOR,
    AREA
}
