package com.cactus.bitacora.biometric.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class FaceTemplateCrypto {
    companion object {
        private const val KEY_ALIAS = "bitacora_face_template_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }

    fun encrypt(embedding: FloatArray): ByteArray {
        val plain = ByteBuffer.allocate(embedding.size * Float.SIZE_BYTES)
        embedding.forEach(plain::putFloat)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.array())
        return cipher.iv + encrypted
    }

    fun decrypt(payload: ByteArray): FloatArray {
        require(payload.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_SIZE))
        )
        val plain = ByteBuffer.wrap(cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)))
        return FloatArray(plain.remaining() / Float.SIZE_BYTES) { plain.float }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}
