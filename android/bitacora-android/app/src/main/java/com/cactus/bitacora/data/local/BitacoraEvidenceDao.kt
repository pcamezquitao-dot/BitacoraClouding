package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface BitacoraEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(evidence: BitacoraEvidenceEntity): Long

    @Update
    suspend fun update(evidence: BitacoraEvidenceEntity)

    @Query("SELECT * FROM bitacora_evidences WHERE bitacoraLocalId = :bitacoraLocalId ORDER BY createdAt ASC")
    suspend fun getForBitacora(bitacoraLocalId: Long): List<BitacoraEvidenceEntity>

    @Query("SELECT * FROM bitacora_evidences WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: Long): BitacoraEvidenceEntity?

    @Query("SELECT * FROM bitacora_evidences WHERE syncStatus IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getBySyncStatuses(statuses: List<SyncStatus>): List<BitacoraEvidenceEntity>

    @Query("SELECT COUNT(*) FROM bitacora_evidences WHERE bitacoraLocalId = :bitacoraLocalId AND evidenceType = :type")
    suspend fun countByType(bitacoraLocalId: Long, type: EvidenceType): Int

    @Query("DELETE FROM bitacora_evidences WHERE localId = :localId")
    suspend fun deleteById(localId: Long)

    @Transaction
    suspend fun insertWithRequiredGps(evidence: BitacoraEvidenceEntity): Long {
        require(evidence.gpsStatus == GpsStatus.READY || evidence.syncStatus == SyncStatus.PENDING_GPS) {
            "La evidencia requiere GPS o debe conservarse como PENDING_GPS"
        }
        return insert(evidence)
    }
}
