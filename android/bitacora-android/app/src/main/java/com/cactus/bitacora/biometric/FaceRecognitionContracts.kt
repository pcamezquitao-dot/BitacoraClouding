package com.cactus.bitacora.biometric

/**
 * Contratos de la prueba técnica. No contienen una implementación activa
 * ni se conectan todavía con el flujo QR/manual.
 */
interface FaceEmbeddingGenerator {
    val modelVersion: String
    val embeddingSize: Int

    suspend fun generateEmbedding(normalizedFace: FloatArray): FloatArray
}

interface FaceMatcher {
    suspend fun findBestMatch(
        capturedEmbedding: FloatArray,
        target: FaceIdentificationTarget,
        threshold: Float = FaceRecognitionConfig.MATCH_THRESHOLD
    ): FaceMatchCandidate?
}

interface LivenessValidator {
    suspend fun validate(observations: List<FaceObservation>): Boolean
}

data class FaceObservation(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val timestampMillis: Long
)
