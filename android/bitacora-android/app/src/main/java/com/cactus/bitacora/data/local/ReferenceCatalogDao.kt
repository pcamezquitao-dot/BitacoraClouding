package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ReferenceCatalogDao {
    @Query("SELECT * FROM participantes_locales WHERE activo = 1 ORDER BY idParticipante")
    suspend fun activeParticipants(): List<ParticipanteLocalEntity>
    @Query("SELECT * FROM participantes_locales WHERE codigoQr = :code LIMIT 1")
    suspend fun participantByCode(code: String): ParticipanteLocalEntity?

    @Query("SELECT * FROM participantes_locales WHERE idParticipante = :id LIMIT 1")
    suspend fun participantById(id: Int): ParticipanteLocalEntity?

    @Query("SELECT * FROM areas_administrativas_locales WHERE idArea = :id LIMIT 1")
    suspend fun areaById(id: Int): AreaAdministrativaLocalEntity?

    @Query("SELECT * FROM areas_administrativas_locales WHERE codigoQr = :code LIMIT 1")
    suspend fun areaByCode(code: String): AreaAdministrativaLocalEntity?

    @Query("SELECT * FROM areas_administrativas_locales WHERE activo = 1 ORDER BY nombreArea")
    suspend fun activeAreas(): List<AreaAdministrativaLocalEntity>

    @Query("SELECT * FROM tipos_participante_locales ORDER BY descripcion, codigo")
    suspend fun participantTypes(): List<TipoParticipanteLocalEntity>

    @Query("SELECT * FROM jornadas_de_trabajo_locales ORDER BY codigoJornada")
    suspend fun workSchedules(): List<WorkScheduleLocalEntity>

    @Query("SELECT * FROM jornadas_de_trabajo_detalle_locales ORDER BY idJornada,diaSemanaNum,numeroTramo")
    suspend fun workScheduleDetails(): List<WorkScheduleDetailLocalEntity>

    @Query("SELECT * FROM empleado_area_locales WHERE activo = 1 ORDER BY idArea, idParticipante")
    suspend fun activeAssignments(): List<EmpleadoAreaLocalEntity>

    @Query("SELECT * FROM calendario_general_local WHERE activo = 1 ORDER BY fechaInicio, idPeriodo")
    suspend fun activeCalendar(): List<CalendarioGeneralLocalEntity>

    @Query(
        "SELECT * FROM calendario_general_local WHERE activo = 1 AND nivel = 'DIA' " +
            "AND fechaInicio >= :first AND fechaInicio <= :last ORDER BY fechaInicio"
    )
    suspend fun calendarDaysBetween(first: String, last: String): List<CalendarioGeneralLocalEntity>

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

    @Query("""
        UPDATE participantes_locales SET tipoDocumento=:tipoDocumento, documento=:documento,
            codigoQr=:codigo, nombre=:nombre, apellido=:apellido,
            fechaNacimiento=:fechaNacimiento, sexo=:sexo, fechaEntrada=:fechaEntrada,
            fechaSalida=:fechaSalida, observaciones=:observaciones, email=:email,
            activo=:activo, pendingAdminUpdate=:pending
        WHERE idParticipante=:id
    """)
    suspend fun updateAdminParticipant(
        id: Int, tipoDocumento: Int, documento: String, codigo: String, nombre: String,
        apellido: String?, fechaNacimiento: String?, sexo: String?, fechaEntrada: String?,
        fechaSalida: String?, observaciones: String?, email: String?, activo: Boolean,
        pending: Boolean
    ): Int

    @Query("SELECT * FROM participantes_locales WHERE pendingAdminUpdate = 1 ORDER BY idParticipante")
    suspend fun pendingAdminParticipants(): List<ParticipanteLocalEntity>

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
    suspend fun upsertParticipantTypes(items: List<TipoParticipanteLocalEntity>)

    @Upsert
    suspend fun upsertCalendar(items: List<CalendarioGeneralLocalEntity>)

    @Upsert
    suspend fun upsertWorkSchedules(items: List<WorkScheduleLocalEntity>)

    @Upsert
    suspend fun upsertWorkScheduleDetails(items: List<WorkScheduleDetailLocalEntity>)

    @Upsert
    suspend fun upsertPreparationState(state: CatalogPreparationStateEntity)

    @Query("DELETE FROM jornadas_de_trabajo_detalle_locales WHERE idDetalle NOT IN (:ids)")
    suspend fun deleteStaleWorkScheduleDetails(ids: List<Long>)

    @Query("DELETE FROM jornadas_de_trabajo_detalle_locales")
    suspend fun deleteAllWorkScheduleDetails()

    @Query("DELETE FROM empleado_area_locales WHERE idEmpleadoArea NOT IN (:ids)")
    suspend fun deleteStaleAssignments(ids: List<Int>)

    @Query("DELETE FROM empleado_area_locales")
    suspend fun deleteAllAssignments()

    @Upsert
    suspend fun upsertSyncState(state: CatalogSyncStateEntity)

    @Query("UPDATE participantes_locales SET activo = 0")
    suspend fun markAllParticipantsInactive()

    @Query("UPDATE areas_administrativas_locales SET activo = 0")
    suspend fun markAllAreasInactive()

    @Query("UPDATE empleado_area_locales SET activo = 0")
    suspend fun markAllAssignmentsInactive()

    @Query("UPDATE tipos_participante_locales SET activo = 0")
    suspend fun markAllParticipantTypesInactive()

    @Query("UPDATE calendario_general_local SET activo = 0")
    suspend fun markAllCalendarInactive()

    @Query("UPDATE jornadas_de_trabajo_locales SET activo = 0")
    suspend fun markAllWorkSchedulesInactive()

    @Query("SELECT COUNT(*) FROM participantes_locales WHERE activo = 1")
    suspend fun participantCount(): Int

    @Query("SELECT COUNT(*) FROM areas_administrativas_locales WHERE activo = 1")
    suspend fun areaCount(): Int

    @Query("SELECT COUNT(*) FROM empleado_area_locales WHERE activo = 1")
    suspend fun assignmentCount(): Int

    @Query("SELECT COUNT(*) FROM tipos_participante_locales WHERE activo = 1")
    suspend fun participantTypeCount(): Int

    @Query("SELECT COUNT(*) FROM calendario_general_local WHERE activo = 1")
    suspend fun calendarCount(): Int

    @Query("SELECT * FROM catalog_sync_state WHERE catalogKey = 'reference_catalogs' LIMIT 1")
    suspend fun syncState(): CatalogSyncStateEntity?

    @Transaction
    suspend fun applySnapshot(
        participants: List<ParticipanteLocalEntity>,
        areas: List<AreaAdministrativaLocalEntity>,
        assignments: List<EmpleadoAreaLocalEntity>,
        participantTypes: List<TipoParticipanteLocalEntity>,
        workSchedules: List<WorkScheduleLocalEntity>,
        workScheduleDetails: List<WorkScheduleDetailLocalEntity>,
        state: CatalogSyncStateEntity,
        preparationState: CatalogPreparationStateEntity,
        calendar: List<CalendarioGeneralLocalEntity> = emptyList()
    ) {
        upsertPreparationState(preparationState.copy(status = "PREPARING", preparedAtMillis = null))
        markAllParticipantsInactive()
        markAllAreasInactive()
        markAllAssignmentsInactive()
        markAllParticipantTypesInactive()
        markAllCalendarInactive()
        markAllWorkSchedulesInactive()
        upsertParticipants(participants)
        upsertAreas(areas)
        upsertAssignments(assignments)
        if (assignments.isEmpty()) deleteAllAssignments()
        else deleteStaleAssignments(assignments.map { it.idEmpleadoArea })
        upsertParticipantTypes(participantTypes)
        upsertWorkSchedules(workSchedules)
        upsertWorkScheduleDetails(workScheduleDetails)
        if (workScheduleDetails.isEmpty()) deleteAllWorkScheduleDetails()
        else deleteStaleWorkScheduleDetails(workScheduleDetails.map { it.idDetalle })
        upsertCalendar(calendar)
        upsertSyncState(state)
        val storedSchedules = this.workSchedules().filter { it.activo }.associateBy { it.idJornada }
        val expectedSchedules = workSchedules.associateBy { it.idJornada }
        check(storedSchedules == expectedSchedules) {
            "Las cabeceras de jornadas no coinciden despues de persistir"
        }
        val storedDetails = this.workScheduleDetails().associateBy { it.idDetalle }
        val expectedDetails = workScheduleDetails.associateBy { it.idDetalle }
        check(storedDetails == expectedDetails) {
            "Los detalles de jornadas no coinciden despues de persistir"
        }
        val storedAssignments = activeAssignments().associateBy { it.idEmpleadoArea }
        val expectedAssignments = assignments.filter { it.activo }.associateBy { it.idEmpleadoArea }
        check(storedAssignments == expectedAssignments) {
            "Las asignaciones no coinciden despues de persistir"
        }
        upsertPreparationState(preparationState)
    }
}
