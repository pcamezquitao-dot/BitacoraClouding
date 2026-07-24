package com.cactus.bitacora.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "bitacora_evidences",
    indices = [
        Index(value = ["bitacoraLocalId"]),
        Index(value = ["bitacoraServerId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["createdAt"]),
        Index(value = ["clientUuid"], unique = true)
    ]
)
data class BitacoraEvidenceEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: Int? = null,
    val bitacoraLocalId: Long,
    val bitacoraServerId: Int? = null,
    @ColumnInfo(defaultValue = "0") val areaId: Int,
    val clientUuid: String,
    val evidenceType: EvidenceType,
    val textContent: String? = null,
    val originalName: String? = null,
    val localFilePath: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val durationSeconds: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val gpsTimestamp: Long? = null,
    val locationProvider: String? = null,
    val gpsStatus: GpsStatus = GpsStatus.PENDING,
    val syncStatus: SyncStatus = SyncStatus.PENDIENTE_CREAR,
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null
)
