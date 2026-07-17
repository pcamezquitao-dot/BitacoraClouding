package com.cactus.bitacora.biometric.technical

import android.graphics.Rect
import kotlin.math.sqrt

data class FaceQuality(
    val accepted: Boolean,
    val message: String,
    val sizeRatio: Float,
    val centerOffset: Float
)

object FaceTechnicalMath {
    const val MIN_SIZE_RATIO = 0.25f
    const val MAX_CENTER_OFFSET = 0.18f

    fun quality(rect: Rect, frameWidth: Int, frameHeight: Int): FaceQuality =
        quality(rect.left, rect.top, rect.right, rect.bottom, frameWidth, frameHeight)

    fun quality(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        frameWidth: Int,
        frameHeight: Int
    ): FaceQuality {
        if (frameWidth <= 0 || frameHeight <= 0) {
            return FaceQuality(false, "Dimensiones de cámara inválidas", 0f, 1f)
        }
        val faceWidth = (right - left).coerceAtLeast(0)
        val faceHeight = (bottom - top).coerceAtLeast(0)
        val sizeRatio = minOf(
            faceWidth.toFloat() / frameWidth,
            faceHeight.toFloat() / frameHeight
        )
        val dx = kotlin.math.abs((left + right) / 2f - frameWidth / 2f) / frameWidth
        val dy = kotlin.math.abs((top + bottom) / 2f - frameHeight / 2f) / frameHeight
        val centerOffset = maxOf(dx, dy)
        val message = when {
            sizeRatio < MIN_SIZE_RATIO -> "Acérquese: el rostro es muy pequeño"
            centerOffset > MAX_CENTER_OFFSET -> "Centre el rostro dentro de la cámara"
            else -> "Rostro centrado y con tamaño suficiente"
        }
        return FaceQuality(
            accepted = sizeRatio >= MIN_SIZE_RATIO && centerOffset <= MAX_CENTER_OFFSET,
            message = message,
            sizeRatio = sizeRatio,
            centerOffset = centerOffset
        )
    }

    fun l2Distance(first: FloatArray, second: FloatArray): Float {
        require(first.size == second.size && first.isNotEmpty())
        var sum = 0f
        for (index in first.indices) {
            val delta = first[index] - second[index]
            sum += delta * delta
        }
        return sqrt(sum)
    }

    fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        require(first.size == second.size && first.isNotEmpty())
        var dot = 0f
        var normFirst = 0f
        var normSecond = 0f
        for (index in first.indices) {
            dot += first[index] * second[index]
            normFirst += first[index] * first[index]
            normSecond += second[index] * second[index]
        }
        if (normFirst == 0f || normSecond == 0f) return 0f
        return dot / (sqrt(normFirst) * sqrt(normSecond))
    }
}
