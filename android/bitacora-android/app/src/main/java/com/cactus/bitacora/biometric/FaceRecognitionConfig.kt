package com.cactus.bitacora.biometric

/**
 * Parámetros centralizados de reconocimiento facial.
 *
 * El umbral inicial es provisional y debe calibrarse con el modelo TFLite
 * seleccionado y pruebas representativas antes de habilitar reconocimiento.
 */
object FaceRecognitionConfig {
    const val MATCH_THRESHOLD = 0.65f
    const val MIN_FACE_SIZE_RATIO = 0.20f
    const val MAX_HEAD_ANGLE_DEGREES = 20f
    const val REQUIRED_ENROLLMENT_CAPTURES = 3
}
