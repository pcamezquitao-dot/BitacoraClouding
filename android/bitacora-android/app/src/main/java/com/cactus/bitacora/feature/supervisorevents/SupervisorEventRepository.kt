package com.cactus.bitacora.feature.supervisorevents

import android.content.Context
import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.SupervisorEventDao
import com.cactus.bitacora.data.local.SupervisorEventLocalEntity
import com.cactus.bitacora.data.local.SyncStatus
import java.io.IOException
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.POST

internal data class SupervisorEventRemoteIn(
    val codigo_supervisor: String,
    val tipo_novedad: Int,
    val id_participante: Int,
    val id_area: Int,
    val fecha_inicio: String,
    val fecha_final: String,
    val hora_inicio: String?,
    val hora_final: String?,
    val observaciones: String?,
    val client_uuid: String
)

internal data class SupervisorEventRemoteOut(val id_novedad: Long)

internal interface SupervisorEventApi {
    @POST("supervisor/novedades")
    suspend fun create(@Body payload: SupervisorEventRemoteIn): SupervisorEventRemoteOut
}

internal data class SupervisorEventSyncResult(
    val reviewed: Int,
    val synced: Int,
    val errors: Int,
    val retryableErrors: Int
)

internal class SupervisorEventRepository(
    private val dao: SupervisorEventDao,
    private val api: SupervisorEventApi
) {
    constructor(context: Context) : this(
        BitacoraDatabase.getInstance(context).supervisorEventDao(),
        NetworkClient.createService(SupervisorEventApi::class.java)
    )

    suspend fun saveOffline(draft: SupervisorEventDraft): SupervisorEventLocalEntity {
        val entity = draft.toLocalEntity()
        val localId = dao.insert(entity)
        return entity.copy(localId = localId)
    }

    suspend fun eventsForParticipant(
        supervisorCode: String,
        participantId: Int
    ): List<SupervisorEventLocalEntity> =
        dao.eventsForParticipant(supervisorCode.trim().uppercase(), participantId)

    suspend fun syncPending(): SupervisorEventSyncResult {
        val pending = dao.pending()
        var synced = 0
        var retryable = 0
        pending.forEach { entity ->
            try {
                val remote = api.create(entity.toRemote())
                dao.updateSync(entity.localId, remote.id_novedad, SyncStatus.SINCRONIZADO, null)
                synced++
            } catch (error: Exception) {
                val isRetryable = error is IOException || error is HttpException && error.code() >= 500
                if (isRetryable) retryable++
                // El endpoint C08 puede no estar desplegado todavía. Se conserva pendiente.
                dao.updateSync(
                    entity.localId,
                    entity.backendId,
                    SyncStatus.PENDIENTE_CREAR,
                    syncErrorMessage(error)
                )
            }
        }
        return SupervisorEventSyncResult(
            reviewed = pending.size,
            synced = synced,
            errors = pending.size - synced,
            retryableErrors = retryable
        )
    }
}

private fun SupervisorEventLocalEntity.toRemote() = SupervisorEventRemoteIn(
    codigo_supervisor = supervisorCode,
    tipo_novedad = tipoNovedad,
    id_participante = idParticipante,
    id_area = idArea,
    fecha_inicio = fechaInicio,
    fecha_final = fechaFinal,
    hora_inicio = horaInicio,
    hora_final = horaFinal,
    observaciones = observaciones,
    client_uuid = clientUuid
)

private fun syncErrorMessage(error: Exception): String = when (error) {
    is HttpException -> "HTTP ${error.code()}: endpoint C08 no disponible o solicitud rechazada"
    else -> error.message ?: "No fue posible sincronizar la novedad"
}.take(500)
