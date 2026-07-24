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

    @Query(
        "SELECT * FROM bitacora_evidences WHERE syncStatus != 'PENDIENTE_ELIMINAR' AND (" +
            "bitacoraLocalId = :bitacoraLocalId " +
            "OR (:bitacoraServerId IS NOT NULL AND bitacoraServerId = :bitacoraServerId)) " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getForBitacora(
        bitacoraLocalId: Long,
        bitacoraServerId: Int? = null
    ): List<BitacoraEvidenceEntity>

    @Query(
        "SELECT * FROM bitacora_evidences WHERE bitacoraLocalId = :bitacoraLocalId " +
            "OR (:bitacoraServerId IS NOT NULL AND bitacoraServerId = :bitacoraServerId)"
    )
    suspend fun getAllForBitacora(
        bitacoraLocalId: Long,
        bitacoraServerId: Int? = null
    ): List<BitacoraEvidenceEntity>

    @Query("SELECT * FROM bitacora_evidences WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: Long): BitacoraEvidenceEntity?

    @Query("SELECT * FROM bitacora_evidences WHERE clientUuid = :clientUuid LIMIT 1")
    suspend fun getByClientUuid(clientUuid: String): BitacoraEvidenceEntity?

    @Query("SELECT * FROM bitacora_evidences WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Int): BitacoraEvidenceEntity?

    @Query("SELECT * FROM bitacora_evidences WHERE syncStatus IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getBySyncStatuses(statuses: List<SyncStatus>): List<BitacoraEvidenceEntity>

    @Query(
        "SELECT COUNT(*) FROM bitacora_evidences " +
            "WHERE syncStatus IN ('PENDIENTE_CREAR','PENDIENTE_ACTUALIZAR','PENDIENTE_ELIMINAR')"
    )
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM bitacora_evidences WHERE syncStatus = 'ERROR'")
    suspend fun countErrors(): Int

    @Query("SELECT COUNT(*) FROM bitacora_evidences WHERE bitacoraLocalId = :bitacoraLocalId AND evidenceType = :type")
    suspend fun countByType(bitacoraLocalId: Long, type: EvidenceType): Int

    @Query("DELETE FROM bitacora_evidences WHERE localId = :localId")
    suspend fun deleteById(localId: Long)

    @Query(
        "DELETE FROM bitacora_evidences WHERE bitacoraLocalId = :bitacoraLocalId " +
            "OR (:bitacoraServerId IS NOT NULL AND bitacoraServerId = :bitacoraServerId)"
    )
    suspend fun deleteForBitacora(bitacoraLocalId: Long, bitacoraServerId: Int? = null)

    @Query(
        """
        UPDATE bitacora_evidences
        SET syncStatus = 'PENDIENTE_ELIMINAR', lastSyncError = NULL
        WHERE localId = :localId
        """
    )
    suspend fun markPendingDelete(localId: Long)

    @Transaction
    suspend fun insertWithRequiredGps(evidence: BitacoraEvidenceEntity): Long {
        require(evidence.gpsStatus == GpsStatus.READY) {
            "La evidencia requiere GPS antes de quedar pendiente de sincronización"
        }
        return insert(evidence)
    }

    @Transaction
    suspend fun mergeRemoteEvidence(evidence: BitacoraEvidenceEntity) {
        val existing = getByClientUuid(evidence.clientUuid)
            ?: evidence.remoteId?.let { getByRemoteId(it) }
        if (existing == null) {
            insert(evidence)
            return
        }
        update(
            evidence.copy(
                localId = existing.localId,
                localFilePath = existing.localFilePath,
                textContent = evidence.textContent ?: existing.textContent,
                altitude = existing.altitude,
                gpsTimestamp = existing.gpsTimestamp,
                locationProvider = existing.locationProvider,
                syncAttempts = existing.syncAttempts
            )
        )
    }
}
