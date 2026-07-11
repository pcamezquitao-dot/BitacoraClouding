package com.cactus.bitacora.data

import android.content.Context
import com.cactus.bitacora.data.local.BitacoraDao
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.data.local.toLocalEntity
import com.cactus.bitacora.data.models.AreaByQrIn
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import retrofit2.HttpException

class BitacoraRepository(
    context: Context,
    private val api: BitacoraApi = Api.create()
) {
    private val bitacoraDao: BitacoraDao =
        BitacoraDatabase.getInstance(context).bitacoraDao()

    suspend fun checkHealth() =
        api.health()

    suspend fun getAreaByQr(qr: String) =
        api.getAreaByQr(AreaByQrIn(qr))

    suspend fun getParticipanteByQr(qr: String) = api.getParticipanteByQr(qr)

    suspend fun getAsignacionActiva(idParticipante: Int) =
        api.getAsignacionActiva(idParticipante)

    suspend fun crearBitacoraDiaria(request: BitacoraDiariaCreate): CreateBitacoraResult {
        val requestWithUuid = request.ensureClientUuid()

        return try {
            val response = api.crearBitacoraDiaria(requestWithUuid)
            bitacoraDao.insert(
                requestWithUuid.toLocalEntity(
                    backendId = response.id_bitacora,
                    syncStatus = SyncStatus.SINCRONIZADO
                )
            )
            CreateBitacoraResult.Sincronizada(response)
        } catch (e: HttpException) {
            if (e.code() in 400..499) throw e
            val localId = bitacoraDao.insert(
                requestWithUuid.toLocalEntity(
                    syncStatus = SyncStatus.PENDIENTE,
                    errorMessage = e.message
                )
            )
            CreateBitacoraResult.Pendiente(
                localId = localId,
                message = "Backend no disponible. Bitacora guardada localmente."
            )
        } catch (e: Exception) {
            val localId = bitacoraDao.insert(
                requestWithUuid.toLocalEntity(
                    syncStatus = SyncStatus.PENDIENTE,
                    errorMessage = e.message
                )
            )
            CreateBitacoraResult.Pendiente(
                localId = localId,
                message = "Backend no disponible. Bitacora guardada localmente."
            )
        }
    }

    suspend fun getBitacoraDiaria(idBitacora: Int) =
        api.getBitacoraDiaria(idBitacora)

    suspend fun getSyncSummary() = SyncSummary(
        pendientes = bitacoraDao.countByStatus(SyncStatus.PENDIENTE),
        sincronizados = bitacoraDao.countByStatus(SyncStatus.SINCRONIZADO),
        errores = bitacoraDao.countByStatus(SyncStatus.ERROR)
    )

    suspend fun sincronizarPendientes(): SyncRunResult {
        val pendientes = bitacoraDao.getByStatuses(
            listOf(SyncStatus.PENDIENTE, SyncStatus.ERROR)
        )
        var sincronizados = 0
        var errores = 0

        pendientes.forEach { local ->
            try {
                val response = api.crearBitacoraDiaria(local.toCreateRequest())
                bitacoraDao.updateSyncState(
                    localId = local.localId,
                    status = SyncStatus.SINCRONIZADO,
                    backendId = response.id_bitacora,
                    errorMessage = null
                )
                sincronizados++
            } catch (e: Exception) {
                bitacoraDao.updateSyncState(
                    localId = local.localId,
                    status = SyncStatus.ERROR,
                    backendId = local.backendId,
                    errorMessage = e.message ?: "No fue posible sincronizar"
                )
                errores++
            }
        }

        return SyncRunResult(
            revisados = pendientes.size,
            sincronizados = sincronizados,
            errores = errores
        )
    }

    private fun BitacoraDiariaCreate.ensureClientUuid() =
        if (client_uuid.isNullOrBlank()) {
            copy(client_uuid = java.util.UUID.randomUUID().toString())
        } else {
            this
        }
}

sealed interface CreateBitacoraResult {
    data class Sincronizada(val bitacora: BitacoraDiariaOut) : CreateBitacoraResult
    data class Pendiente(val localId: Long, val message: String) : CreateBitacoraResult
}

data class SyncSummary(
    val pendientes: Int,
    val sincronizados: Int,
    val errores: Int
)

data class SyncRunResult(
    val revisados: Int,
    val sincronizados: Int,
    val errores: Int
)
