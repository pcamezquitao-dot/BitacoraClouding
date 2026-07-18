package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BitacoraDao {
    @Insert
    suspend fun insert(bitacora: BitacoraLocalEntity): Long

    @Query("SELECT * FROM bitacoras_locales WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: Long): BitacoraLocalEntity?

    @Query("SELECT * FROM bitacoras_locales WHERE syncStatus = :status ORDER BY createdAtMillis ASC")
    suspend fun getByStatus(status: SyncStatus): List<BitacoraLocalEntity>

    @Query("SELECT * FROM bitacoras_locales WHERE syncStatus IN (:statuses) ORDER BY createdAtMillis ASC")
    suspend fun getByStatuses(statuses: List<SyncStatus>): List<BitacoraLocalEntity>

    @Query("SELECT COUNT(*) FROM bitacoras_locales WHERE syncStatus = :status")
    suspend fun countByStatus(status: SyncStatus): Int

    @Query(
        "SELECT COUNT(*) FROM bitacoras_locales " +
            "WHERE syncStatus IN ('PENDIENTE_CREAR','PENDIENTE_ACTUALIZAR','PENDIENTE_ELIMINAR')"
    )
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM bitacoras_locales WHERE syncStatus = 'ERROR'")
    suspend fun countErrors(): Int

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
