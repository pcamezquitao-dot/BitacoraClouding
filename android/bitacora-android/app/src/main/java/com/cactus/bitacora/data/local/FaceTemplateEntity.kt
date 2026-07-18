package com.cactus.bitacora.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_templates",
    indices = [
        Index(value = ["participantCode"]),
        Index(value = ["active"])
    ]
)
data class FaceTemplateEntity(
    @PrimaryKey val participantId: Int,
    val localSyncUuid: String,
    val participantCode: String,
    val displayName: String,
    val encryptedEmbedding: ByteArray,
    val enrolledAtMillis: Long,
    val modelVersion: String,
    val active: Boolean,
    val remoteTemplateId: Int? = null,
    val embeddingSha256: String? = null,
    val encryptionVersion: String = "local-keystore-aesgcm-v1",
    val centralSyncState: String = "PENDIENTE_CREAR",
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null
)
