package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(
    tableName = "jornadas_de_trabajo_locales",
    indices = [Index(value = ["codigoJornada"], unique = true), Index(value = ["activo"]) ]
)
data class WorkScheduleLocalEntity(
    @androidx.room.PrimaryKey val idJornada: Long,
    val codigoJornada: String,
    val nombreJornada: String,
    val minutosObjetivoSemana: Int,
    val toleranciaEntradaMin: Int,
    val toleranciaSalidaMin: Int,
    val vigenciaDesde: String,
    val vigenciaHasta: String?,
    val activo: Boolean,
    @androidx.room.ColumnInfo(defaultValue = "1") val aplicaControlHorario: Boolean,
    val observaciones: String?,
    val fechaCreacion: String?,
    val fechaActualizacion: String?,
    val totalProgramadoSemana: Int,
    val syncedAtMillis: Long
)

@Entity(
    tableName = "jornadas_de_trabajo_detalle_locales",
    indices = [
        Index(value = ["idJornada"]),
        Index(value = ["idJornada", "diaSemanaNum", "numeroTramo"], unique = true)
    ]
)
data class WorkScheduleDetailLocalEntity(
    @androidx.room.PrimaryKey val idDetalle: Long,
    val idJornada: Long,
    val diaSemanaNum: Int,
    val numeroTramo: Int,
    val esLaborable: Boolean,
    val horaEntradaMin: Int?,
    val horaSalidaMin: Int?,
    val salidaDiaSiguiente: Boolean,
    val descansoMin: Int,
    val descansoRemunerado: Boolean,
    val observaciones: String?,
    val minutosProgramados: Int
)

@Entity(tableName = "catalog_preparation_state")
data class CatalogPreparationStateEntity(
    @androidx.room.PrimaryKey val preparationKey: String = "work_schedules_not_null",
    val status: String,
    val preparedAtMillis: Long?,
    val assignmentCount: Int,
    val nullScheduleCount: Int,
    val orphanScheduleCount: Int,
    val validationMessage: String?
)

@Dao
interface WorkScheduleDao {
    @Query("SELECT * FROM jornadas_de_trabajo_locales ORDER BY codigoJornada")
    suspend fun schedules(): List<WorkScheduleLocalEntity>

    @Query("SELECT * FROM jornadas_de_trabajo_detalle_locales ORDER BY idJornada,diaSemanaNum,numeroTramo")
    suspend fun details(): List<WorkScheduleDetailLocalEntity>

    @Upsert
    suspend fun upsertSchedules(values: List<WorkScheduleLocalEntity>)

    @Upsert
    suspend fun upsertDetails(values: List<WorkScheduleDetailLocalEntity>)

    @Query("DELETE FROM jornadas_de_trabajo_detalle_locales")
    suspend fun clearDetails()

    @Query("DELETE FROM jornadas_de_trabajo_locales")
    suspend fun clearSchedules()

    @Query("DELETE FROM jornadas_de_trabajo_detalle_locales WHERE idJornada=:id")
    suspend fun clearDetails(id: Long)

    @Transaction
    suspend fun replaceAll(headers: List<WorkScheduleLocalEntity>, details: List<WorkScheduleDetailLocalEntity>) {
        clearDetails(); clearSchedules(); upsertSchedules(headers); upsertDetails(details)
    }

    @Transaction
    suspend fun replaceOne(header: WorkScheduleLocalEntity, details: List<WorkScheduleDetailLocalEntity>) {
        upsertSchedules(listOf(header)); clearDetails(header.idJornada); upsertDetails(details)
    }
}
