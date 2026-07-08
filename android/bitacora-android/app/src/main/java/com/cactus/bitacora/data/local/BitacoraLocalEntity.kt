package com.cactus.bitacora.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cactus.bitacora.data.models.BitacoraDiariaCreate

@Entity(tableName = "bitacoras_locales")
data class BitacoraLocalEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val backendId: Int? = null,
    val idEmpleado: Int,
    val idSupervisor: Int? = null,
    val tsInMin: Int? = null,
    val tsOutMin: Int? = null,
    val tipoAnotacion: Int? = null,
    val observaciones: String? = null,
    val clientUuid: String,
    val syncStatus: SyncStatus,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toCreateRequest() = BitacoraDiariaCreate(
        id_empleado = idEmpleado,
        id_supervisor = idSupervisor,
        ts_in_min = tsInMin,
        ts_out_min = tsOutMin,
        tipo_anotacion = tipoAnotacion,
        observaciones = observaciones,
        client_uuid = clientUuid
    )
}

fun BitacoraDiariaCreate.toLocalEntity(
    backendId: Int? = null,
    syncStatus: SyncStatus,
    errorMessage: String? = null
) = BitacoraLocalEntity(
    backendId = backendId,
    idEmpleado = id_empleado,
    idSupervisor = id_supervisor,
    tsInMin = ts_in_min,
    tsOutMin = ts_out_min,
    tipoAnotacion = tipo_anotacion,
    observaciones = observaciones,
    clientUuid = client_uuid ?: java.util.UUID.randomUUID().toString(),
    syncStatus = syncStatus,
    errorMessage = errorMessage
)
