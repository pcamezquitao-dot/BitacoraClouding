package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction

@Dao
interface FaceTemplateDao {
    @Upsert
    suspend fun upsert(template: FaceTemplateEntity)

    @Transaction
    suspend fun replaceCentralCopiesAtomically(templates: List<FaceTemplateEntity>) {
        templates.forEach { upsert(it) }
    }

    @Query("SELECT * FROM face_templates WHERE participantId = :participantId LIMIT 1")
    suspend fun getByParticipantId(participantId: Int): FaceTemplateEntity?

    @Query("SELECT * FROM face_templates WHERE active = 1 ORDER BY participantId")
    suspend fun getActive(): List<FaceTemplateEntity>

    @Query(
        "SELECT * FROM face_templates " +
            "WHERE centralSyncState IN " +
            "('PENDIENTE_CREAR','PENDIENTE_ACTUALIZAR','PENDIENTE_ELIMINAR','ERROR')"
    )
    suspend fun getPendingCentralSync(): List<FaceTemplateEntity>

    @Query("SELECT COUNT(*) FROM face_templates WHERE active = 1")
    suspend fun countActive(): Int

    @Query("UPDATE face_templates SET active = 0 WHERE participantId = :participantId")
    suspend fun deactivate(participantId: Int)

    @Query(
        """
        UPDATE face_templates
        SET remoteTemplateId = :remoteTemplateId,
            embeddingSha256 = :embeddingSha256,
            centralSyncState = 'SINCRONIZADO',
            lastSyncError = NULL
        WHERE participantId = :participantId
        """
    )
    suspend fun markCentralSynced(
        participantId: Int,
        remoteTemplateId: Int,
        embeddingSha256: String
    )

    @Query(
        """
        UPDATE face_templates
        SET centralSyncState = 'ERROR',
            syncAttempts = syncAttempts + 1,
            lastSyncError = :message
        WHERE participantId = :participantId
        """
    )
    suspend fun markCentralError(participantId: Int, message: String)

    @Query("UPDATE face_templates SET active = 0 WHERE centralSyncState = 'SINCRONIZADO'")
    suspend fun deactivateCentralCopies()

    @Query(
        """
        UPDATE face_templates
        SET active = 0,
            centralSyncState = 'PENDIENTE_ELIMINAR',
            lastSyncError = NULL
        WHERE participantId = :participantId
        """
    )
    suspend fun markPendingDelete(participantId: Int)

    @Query(
        "UPDATE face_templates SET localSyncUuid = :localSyncUuid " +
            "WHERE participantId = :participantId"
    )
    suspend fun updateLocalSyncUuid(participantId: Int, localSyncUuid: String)

    @Query(
        "SELECT COUNT(*) FROM face_templates " +
            "WHERE centralSyncState IN " +
            "('PENDIENTE_CREAR','PENDIENTE_ACTUALIZAR','PENDIENTE_ELIMINAR')"
    )
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM face_templates WHERE centralSyncState = 'ERROR'")
    suspend fun countErrors(): Int

    @Query("DELETE FROM face_templates WHERE participantId = :participantId")
    suspend fun deleteByParticipantId(participantId: Int)
}
