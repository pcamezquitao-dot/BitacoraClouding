package com.cactus.bitacora.biometric.technical

import android.content.Context
import android.graphics.Bitmap
import com.cactus.bitacora.biometric.FaceEmbeddingGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceNetEmbeddingGenerator(context: Context) : FaceEmbeddingGenerator, Closeable {
    companion object {
        const val MODEL_ASSET = "facenet.tflite"
        const val INPUT_SIZE = 160
        const val OUTPUT_SIZE = 128
    }

    private val interpreter = Interpreter(
        context.assets.openFd(MODEL_ASSET).use { descriptor ->
            descriptor.createInputStream().channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        },
        Interpreter.Options().setNumThreads(4)
    )

    override val modelVersion: String = "FaceNet-160/128"
    override val embeddingSize: Int = OUTPUT_SIZE

    init {
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 160, 160, 3))) {
            "Entrada FaceNet inesperada: ${interpreter.getInputTensor(0).shape().contentToString()}"
        }
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 128))) {
            "Salida FaceNet inesperada: ${interpreter.getOutputTensor(0).shape().contentToString()}"
        }
    }

    suspend fun generateEmbedding(face: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val scaled = Bitmap.createScaledBitmap(face, INPUT_SIZE, INPUT_SIZE, true)
        try {
            generateEmbedding(bitmapToNormalizedRgb(scaled))
        } finally {
            if (scaled !== face) scaled.recycle()
        }
    }

    override suspend fun generateEmbedding(normalizedFace: FloatArray): FloatArray =
        withContext(Dispatchers.Default) {
            require(normalizedFace.size == INPUT_SIZE * INPUT_SIZE * 3)
            val input = ByteBuffer.allocateDirect(normalizedFace.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            normalizedFace.forEach(input::putFloat)
            input.rewind()
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            synchronized(interpreter) {
                interpreter.run(input, output)
            }
            output[0]
        }

    private fun bitmapToNormalizedRgb(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        val values = FloatArray(pixels.size * 3)
        var offset = 0
        pixels.forEach { pixel ->
            values[offset++] = ((pixel shr 16 and 0xff) - 127.5f) / 128f
            values[offset++] = ((pixel shr 8 and 0xff) - 127.5f) / 128f
            values[offset++] = ((pixel and 0xff) - 127.5f) / 128f
        }
        return values
    }

    override fun close() {
        interpreter.close()
    }
}
