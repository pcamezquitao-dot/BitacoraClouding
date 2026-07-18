package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface FaceTemplateDao {
    @Upsert
    suspend fun upsert(template: FaceTemplateEntity)

    @Query("SELECT * FROM face_templates WHERE participantId = :participantId LIMIT 1")
    suspend fun getByParticipantId(participantId: Int): FaceTemplateEntity?

    @Query("SELECT * FROM face_templates WHERE active = 1 ORDER BY participantId")
    suspend fun getActive(): List<FaceTemplateEntity>

    @Query(
        "SELECT * FROM face_templates " +
            "WHERE centralSyncState IN ('PENDING', 'ERROR') AND active = 1"
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
            centralSyncState = 'SYNCED',
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
        SET centralSyncState = 'ERROR', lastSyncError = :message
        WHERE participantId = :participantId
        """
    )
    suspend fun markCentralError(participantId: Int, message: String)

    @Query("UPDATE face_templates SET active = 0 WHERE centralSyncState = 'SYNCED'")
    suspend fun deactivateCentralCopies()

    @Query("DELETE FROM face_templates WHERE participantId = :participantId")
    suspend fun deleteByParticipantId(participantId: Int)
}
