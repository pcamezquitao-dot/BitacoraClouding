package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ReferenceCatalogDao {
    @Query("SELECT * FROM participantes_locales WHERE codigoQr = :code LIMIT 1")
    suspend fun participantByCode(code: String): ParticipanteLocalEntity?

    @Query("SELECT * FROM participantes_locales WHERE idParticipante = :id LIMIT 1")
    suspend fun participantById(id: Int): ParticipanteLocalEntity?

    @Query("SELECT * FROM areas_administrativas_locales WHERE idArea = :id LIMIT 1")
    suspend fun areaById(id: Int): AreaAdministrativaLocalEntity?

    @Query("SELECT * FROM areas_administrativas_locales WHERE codigoQr = :code LIMIT 1")
    suspend fun areaByCode(code: String): AreaAdministrativaLocalEntity?

    @Query(
        "SELECT * FROM empleado_area_locales " +
            "WHERE idParticipante = :participantId AND activo = 1 ORDER BY idArea"
    )
    suspend fun activeAssignmentsForParticipant(participantId: Int): List<EmpleadoAreaLocalEntity>

    @Query(
        "SELECT * FROM empleado_area_locales " +
            "WHERE idArea = :areaId AND activo = 1 ORDER BY idParticipante"
    )
    suspend fun activeAssignmentsForArea(areaId: Int): List<EmpleadoAreaLocalEntity>

    @Query(
        "SELECT * FROM empleado_area_locales WHERE idParticipante = :participantId " +
            "AND idArea = :areaId AND activo = 1 LIMIT 1"
    )
    suspend fun activeAssignment(participantId: Int, areaId: Int): EmpleadoAreaLocalEntity?

    @Upsert
    suspend fun upsertParticipant(item: ParticipanteLocalEntity)

    @Upsert
    suspend fun upsertArea(item: AreaAdministrativaLocalEntity)

    @Upsert
    suspend fun upsertAssignment(item: EmpleadoAreaLocalEntity)

    @Upsert
    suspend fun upsertParticipants(items: List<ParticipanteLocalEntity>)

    @Upsert
    suspend fun upsertAreas(items: List<AreaAdministrativaLocalEntity>)

    @Upsert
    suspend fun upsertAssignments(items: List<EmpleadoAreaLocalEntity>)

    @Upsert
    suspend fun upsertSyncState(state: CatalogSyncStateEntity)

    @Query("UPDATE participantes_locales SET activo = 0")
    suspend fun markAllParticipantsInactive()

    @Query("UPDATE areas_administrativas_locales SET activo = 0")
    suspend fun markAllAreasInactive()

    @Query("UPDATE empleado_area_locales SET activo = 0")
    suspend fun markAllAssignmentsInactive()

    @Query("SELECT COUNT(*) FROM participantes_locales WHERE activo = 1")
    suspend fun participantCount(): Int

    @Query("SELECT COUNT(*) FROM areas_administrativas_locales WHERE activo = 1")
    suspend fun areaCount(): Int

    @Query("SELECT COUNT(*) FROM empleado_area_locales WHERE activo = 1")
    suspend fun assignmentCount(): Int

    @Query("SELECT * FROM catalog_sync_state WHERE catalogKey = 'reference_catalogs' LIMIT 1")
    suspend fun syncState(): CatalogSyncStateEntity?

    @Transaction
    suspend fun applySnapshot(
        participants: List<ParticipanteLocalEntity>,
        areas: List<AreaAdministrativaLocalEntity>,
        assignments: List<EmpleadoAreaLocalEntity>,
        state: CatalogSyncStateEntity
    ) {
        markAllParticipantsInactive()
        markAllAreasInactive()
        markAllAssignmentsInactive()
        upsertParticipants(participants)
        upsertAreas(areas)
        upsertAssignments(assignments)
        upsertSyncState(state)
    }
}
