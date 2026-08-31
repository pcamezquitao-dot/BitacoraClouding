package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface BitacoraDao {
    @Insert
    suspend fun insert(bitacora: BitacoraLocalEntity): Long

    @Insert
    suspend fun insertAll(bitacoras: List<BitacoraLocalEntity>): List<Long>

    @Query("SELECT * FROM bitacoras_locales WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: Long): BitacoraLocalEntity?

    @Query("SELECT * FROM bitacoras_locales WHERE backendId = :backendId LIMIT 1")
    suspend fun getByBackendId(backendId: Int): BitacoraLocalEntity?

    @Query("SELECT * FROM bitacoras_locales WHERE clientUuid = :clientUuid LIMIT 1")
    suspend fun getByClientUuid(clientUuid: String): BitacoraLocalEntity?

    @Query("SELECT * FROM bitacoras_locales WHERE clientUuid IN (:clientUuids)")
    suspend fun getByClientUuids(clientUuids: List<String>): List<BitacoraLocalEntity>

    @Query(
        "SELECT * FROM bitacoras_locales WHERE idEmpleado = :participantId " +
            "AND tsInMin >= :startMinute AND tsInMin < :endMinute ORDER BY tsInMin, localId"
    )
    suspend fun participantRecordsBetween(
        participantId: Int,
        startMinute: Int,
        endMinute: Int
    ): List<BitacoraLocalEntity>

    @Update
    suspend fun update(bitacora: BitacoraLocalEntity)

    @Transaction
    suspend fun mergeRemoteBitacora(remote: BitacoraLocalEntity) {
        val existing = remote.backendId?.let { getByBackendId(it) }
            ?: getByClientUuid(remote.clientUuid)
        if (existing == null) {
            insert(remote)
        } else {
            update(
                remote.copy(
                    localId = existing.localId,
                    qrArea = existing.qrArea,
                    openLatitude = existing.openLatitude,
                    openLongitude = existing.openLongitude,
                    openAccuracy = existing.openAccuracy,
                    openAltitude = existing.openAltitude,
                    openGpsTimestamp = existing.openGpsTimestamp,
                    openLocationProvider = existing.openLocationProvider,
                    closeLatitude = existing.closeLatitude,
                    closeLongitude = existing.closeLongitude,
                    closeAccuracy = existing.closeAccuracy,
                    closeAltitude = existing.closeAltitude,
                    closeGpsTimestamp = existing.closeGpsTimestamp,
                    closeLocationProvider = existing.closeLocationProvider,
                    syncAttempts = existing.syncAttempts,
                    createdAtMillis = existing.createdAtMillis
                )
            )
        }
    }

    @Query(
        """
        SELECT b.*,
            (SELECT COUNT(DISTINCT CASE
                WHEN TRIM(e.clientUuid) <> '' THEN 'u:' || e.clientUuid
                WHEN e.remoteId IS NOT NULL THEN 'r:' || e.remoteId
                ELSE 'l:' || e.localId END)
             FROM bitacora_evidences e
             WHERE e.syncStatus != 'PENDIENTE_ELIMINAR'
                AND (e.bitacoraLocalId = b.localId
                OR (b.backendId IS NOT NULL AND e.bitacoraServerId = b.backendId))) AS evidenceCount
        FROM bitacoras_locales b
        ORDER BY b.createdAtMillis DESC
        """
    )
    suspend fun getAllForQuery(): List<BitacoraQueryHeader>

    @Query(
        """
        SELECT b.*,
            (SELECT COUNT(DISTINCT CASE
                WHEN TRIM(e.clientUuid) <> '' THEN 'u:' || e.clientUuid
                WHEN e.remoteId IS NOT NULL THEN 'r:' || e.remoteId
                ELSE 'l:' || e.localId END)
             FROM bitacora_evidences e
             WHERE e.syncStatus != 'PENDIENTE_ELIMINAR'
                AND (e.bitacoraLocalId = b.localId
                OR (b.backendId IS NOT NULL AND e.bitacoraServerId = b.backendId))) AS evidenceCount
        FROM bitacoras_locales b
        WHERE b.tipoAnotacion IN (4, 5)
        ORDER BY b.tsInMin DESC, b.createdAtMillis DESC, b.localId DESC
        """
    )
    suspend fun getAllMovementsForQuery(): List<BitacoraQueryHeader>

    @Query(
        """
        SELECT b.*,
            (SELECT COUNT(DISTINCT CASE
                WHEN TRIM(e.clientUuid) <> '' THEN 'u:' || e.clientUuid
                WHEN e.remoteId IS NOT NULL THEN 'r:' || e.remoteId
                ELSE 'l:' || e.localId END)
             FROM bitacora_evidences e
             WHERE e.syncStatus != 'PENDIENTE_ELIMINAR'
                AND (e.bitacoraLocalId = b.localId
                OR (b.backendId IS NOT NULL AND e.bitacoraServerId = b.backendId))) AS evidenceCount
        FROM bitacoras_locales b
        WHERE b.tipoAnotacion IN (4, 5)
          AND b.idEmpleado IN (:participantIds)
        ORDER BY b.tsInMin DESC, b.createdAtMillis DESC, b.localId DESC
        """
    )
    suspend fun getMovementsForParticipants(
        participantIds: List<Int>
    ): List<BitacoraQueryHeader>

    @Query("SELECT * FROM bitacoras_locales WHERE syncStatus = :status ORDER BY createdAtMillis ASC")
    suspend fun getByStatus(status: SyncStatus): List<BitacoraLocalEntity>

    @Query("SELECT * FROM bitacoras_locales WHERE syncStatus IN (:statuses) ORDER BY createdAtMillis ASC")
    suspend fun getByStatuses(statuses: List<SyncStatus>): List<BitacoraLocalEntity>

    @Query(
        "SELECT * FROM bitacoras_locales WHERE syncStatus = 'SINCRONIZADO' AND backendId IS NOT NULL ORDER BY localId"
    )
    suspend fun getSyncedWithBackendId(): List<BitacoraLocalEntity>

    @Query(
        """
        SELECT * FROM bitacoras_locales
        WHERE idSupervisor = :supervisorId
          AND origenBitacora = 'SUPERVISOR'
          AND tipoAnotacion IN (4, 5)
          AND tsInMin >= :startMinute AND tsInMin < :endMinute
        ORDER BY tsInMin DESC, localId DESC
        """
    )
    suspend fun supervisorMovementsBetween(
        supervisorId: Int,
        startMinute: Int,
        endMinute: Int
    ): List<BitacoraLocalEntity>

    @Query("SELECT COUNT(*) FROM bitacoras_locales WHERE syncStatus = :status")
    suspend fun countByStatus(status: SyncStatus): Int

    @Query(
        "SELECT COUNT(*) FROM bitacoras_locales " +
            "WHERE syncStatus IN ('PENDIENTE_CREAR','PENDIENTE_ACTUALIZAR','PENDIENTE_ELIMINAR')"
    )
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM bitacoras_locales WHERE syncStatus = 'ERROR'")
    suspend fun countErrors(): Int

    @Query("SELECT COUNT(*) FROM bitacoras_locales")
    suspend fun countAll(): Int

    @Query("DELETE FROM bitacoras_locales WHERE localId = :localId")
    suspend fun deleteById(localId: Long)

    @Query(
        "UPDATE bitacoras_locales SET syncAttempts = syncAttempts + 1 " +
            "WHERE localId = :localId"
    )
    suspend fun incrementSyncAttempts(localId: Long)

    @Query(
        """
        UPDATE bitacoras_locales
        SET syncStatus = :status,
            backendId = :backendId,
            errorMessage = :errorMessage,
            updatedAtMillis = :updatedAtMillis
        WHERE localId = :localId
        """
    )
    suspend fun updateSyncState(
        localId: Long,
        status: SyncStatus,
        backendId: Int?,
        errorMessage: String?,
        updatedAtMillis: Long = System.currentTimeMillis()
    )
}

data class BitacoraQueryHeader(
    @Embedded val bitacora: BitacoraLocalEntity,
    val evidenceCount: Int
)
