package com.cactus.bitacora.data.local

import com.cactus.bitacora.biometric.local.LocalFaceTemplateRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceTemplateEntityTest {
    @Test
    fun storesOnlyEncryptedTemplateAndParticipantMetadata() {
        val encrypted = byteArrayOf(1, 2, 3, 4)
        val entity = FaceTemplateEntity(
            participantId = 9,
            participantCode = "P0009",
            displayName = "Supervisor prueba",
            encryptedEmbedding = encrypted,
            enrolledAtMillis = 1234L,
            modelVersion = "FaceNet-160/128",
            active = true
        )

        assertEquals(9, entity.participantId)
        assertEquals("P0009", entity.participantCode)
        assertArrayEquals(encrypted, entity.encryptedEmbedding)
        assertTrue(entity.active)
    }

    @Test
    fun matchingThresholdIsNormalized() {
        assertTrue(LocalFaceTemplateRepository.MATCH_THRESHOLD in 0f..1f)
    }

    @Test
    fun localTemplateTracksCentralSynchronizationWithoutRoleData() {
        val entity = FaceTemplateEntity(
            participantId = 2,
            participantCode = "P0002",
            displayName = "Participante",
            encryptedEmbedding = byteArrayOf(1),
            enrolledAtMillis = 1L,
            modelVersion = "FaceNet-160/128",
            active = true,
            remoteTemplateId = 17,
            embeddingSha256 = "a".repeat(64),
            centralSyncState = "SYNCED"
        )

        assertEquals(2, entity.participantId)
        assertEquals(17, entity.remoteTemplateId)
        assertEquals("SYNCED", entity.centralSyncState)
        assertTrue(
            FaceTemplateEntity::class.java.declaredFields.none {
                it.name.contains("role", ignoreCase = true)
            }
        )
    }
}
