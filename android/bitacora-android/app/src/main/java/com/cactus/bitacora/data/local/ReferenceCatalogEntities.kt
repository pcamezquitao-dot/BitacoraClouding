package com.cactus.bitacora.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "participantes_locales",
    primaryKeys = ["idParticipante"],
    indices = [
        Index(value = ["codigoQr"], unique = true),
        Index(value = ["activo"])
    ]
)
data class ParticipanteLocalEntity(
    val idParticipante: Int,
    val codigoQr: String,
    val nombre: String?,
    val apellido: String?,
    val documento: String?,
    val activo: Boolean = true,
    val updatedAtServer: String? = null,
    val syncedAtMillis: Long
)

@Entity(
    tableName = "areas_administrativas_locales",
    primaryKeys = ["idArea"],
    indices = [
        Index(value = ["codigoQr"], unique = true),
        Index(value = ["activo"])
    ]
)
data class AreaAdministrativaLocalEntity(
    val idArea: Int,
    val codigoQr: String,
    val nombreArea: String,
    val activo: Boolean = true,
    val updatedAtServer: String? = null,
    val syncedAtMillis: Long,
    val nombreCorto: String? = null,
    val idPadre: Int? = null
)

@Entity(
    tableName = "empleado_area_locales",
    primaryKeys = ["idParticipante", "idArea"],
    indices = [
        Index(value = ["idParticipante"]),
        Index(value = ["idArea"]),
        Index(value = ["activo"])
    ]
)
data class EmpleadoAreaLocalEntity(
    val idParticipante: Int,
    val idArea: Int,
    val cargo: Int?,
    val fechaFinal: String? = null,
    val activo: Boolean = true,
    val updatedAtServer: String? = null,
    val syncedAtMillis: Long,
    val fechaInicia: String? = null
)

@Entity(
    tableName = "tipos_participante_locales",
    primaryKeys = ["codigo"],
    indices = [Index(value = ["activo"])]
)
data class TipoParticipanteLocalEntity(
    val codigo: Int,
    val descripcion: String,
    val capacidadesCsv: String,
    val activo: Boolean = true,
    val syncedAtMillis: Long
) {
    val capacidades: Set<String>
        get() = capacidadesCsv.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(String::uppercase)
            .toSet()
}

@Entity(tableName = "catalog_sync_state")
data class CatalogSyncStateEntity(
    @androidx.room.PrimaryKey val catalogKey: String = "reference_catalogs",
    val lastSuccessfulSyncMillis: Long?,
    val participantCount: Int = 0,
    val areaCount: Int = 0,
    val assignmentCount: Int = 0,
    val participantTypeCount: Int = 0,
    val lastError: String? = null
)
