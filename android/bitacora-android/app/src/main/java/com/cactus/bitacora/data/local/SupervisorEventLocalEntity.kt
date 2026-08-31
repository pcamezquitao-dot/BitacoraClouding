package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "supervisor_novedades_locales",
    indices = [Index(value = ["clientUuid"], unique = true), Index("syncStatus")]
)
data class SupervisorEventLocalEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val backendId: Long? = null,
    val clientUuid: String,
    val supervisorCode: String,
    val tipoNovedad: Int,
    val idParticipante: Int,
    val idArea: Int,
    val fechaInicio: String,
    val fechaFinal: String,
    val horaInicio: String? = null,
    val horaFinal: String? = null,
    val observaciones: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDIENTE_CREAR,
    val syncAttempts: Int = 0,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface SupervisorEventDao {
    @Insert
    suspend fun insert(value: SupervisorEventLocalEntity): Long

    @Query("SELECT * FROM supervisor_novedades_locales WHERE syncStatus IN ('PENDIENTE_CREAR','ERROR') ORDER BY createdAtMillis")
    suspend fun pending(): List<SupervisorEventLocalEntity>

    @Query(
        "SELECT * FROM supervisor_novedades_locales " +
            "WHERE supervisorCode=:supervisorCode AND idParticipante=:participantId " +
            "ORDER BY createdAtMillis DESC"
    )
    suspend fun eventsForParticipant(
        supervisorCode: String,
        participantId: Int
    ): List<SupervisorEventLocalEntity>

    @Query("UPDATE supervisor_novedades_locales SET backendId=:backendId,syncStatus=:status,syncAttempts=syncAttempts+1,errorMessage=:error,updatedAtMillis=:updated WHERE localId=:id")
    suspend fun updateSync(
        id: Long,
        backendId: Long?,
        status: SyncStatus,
        error: String?,
        updated: Long = System.currentTimeMillis()
    )
}
