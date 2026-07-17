package com.cactus.bitacora.ui.bitacora

enum class CaptureMode {
    CLOSED,
    QR_SCANNING,
    MANUAL_ENTRY,
    PHOTO_CAPTURE,
    FACE_DETECTING,
    FACE_MATCHING,
    AWAITING_FACE_CONFIRMATION,
    VALIDATING,
    VALIDATED,
    ERROR
}

enum class CaptureTarget {
    EMPLEADO,
    SUPERVISOR,
    AREA
}
