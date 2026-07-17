package com.cactus.bitacora.biometric

enum class FaceIdentificationTarget {
    EMPLEADO,
    SUPERVISOR
}

enum class FaceRecognitionResult {
    CONFIRMADO,
    RECHAZADO,
    SIN_COINCIDENCIA,
    VARIAS_CARAS,
    PERSONA_NO_AUTORIZADA,
    CANCELADO,
    PRUEBA_DE_VIDA_FALLIDA,
    ERROR
}

data class FaceMatchCandidate(
    val participantId: Int,
    val displayName: String,
    val similarity: Float,
    val target: FaceIdentificationTarget,
    val supervisorAuthorized: Boolean
)
