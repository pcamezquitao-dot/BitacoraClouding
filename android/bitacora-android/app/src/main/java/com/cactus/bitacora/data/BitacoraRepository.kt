package com.cactus.bitacora.data

import android.content.Context
import com.cactus.bitacora.data.local.BitacoraDao
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.GpsStatus
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.data.local.toLocalEntity
import com.cactus.bitacora.data.models.AreaByQrIn
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import retrofit2.HttpException
import com.cactus.bitacora.location.LocationSnapshot
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class BitacoraRepository(
    context: Context,
    private val api: BitacoraApi = Api.create()
) {
    private val bitacoraDao: BitacoraDao =
        BitacoraDatabase.getInstance(context).bitacoraDao()
    private val evidenceDao = BitacoraDatabase.getInstance(context).evidenceDao()

    suspend fun checkHealth() =
        api.health()

    suspend fun getAreaByQr(qr: String) =
        api.getAreaByQr(AreaByQrIn(qr))

    suspend fun getParticipanteByQr(qr: String) = api.getParticipanteByQr(qr)

    suspend fun getAsignacionActiva(idParticipante: Int) =
        api.getAsignacionActiva(idParticipante)

    suspend fun crearBitacoraDiaria(
        request: BitacoraDiariaCreate,
        openLocation: LocationSnapshot? = null,
        closeLocation: LocationSnapshot? = null
    ): CreateBitacoraResult {
        val requestWithUuid = request.ensureClientUuid()
        val localId = bitacoraDao.insert(
            requestWithUuid.toLocalEntity(
                syncStatus = SyncStatus.PENDIENTE,
                openLocation = openLocation,
                closeLocation = closeLocation
            )
        )

        return try {
            val response = api.crearBitacoraDiaria(requestWithUuid)
            bitacoraDao.updateSyncState(
                localId = localId,
                status = SyncStatus.SINCRONIZADO,
                backendId = response.id_bitacora,
                errorMessage = null
            )
            CreateBitacoraResult.Sincronizada(localId, response)
        } catch (e: HttpException) {
            val status = if (e.code() in 400..499) SyncStatus.ERROR else SyncStatus.PENDIENTE
            bitacoraDao.updateSyncState(
                localId = localId,
                status = status,
                backendId = null,
                errorMessage = e.message()
            )
            CreateBitacoraResult.Pendiente(
                localId = localId,
                message = "Bitácora guardada localmente. Sincronización pendiente: ${e.message()}"
            )
        } catch (e: Exception) {
            bitacoraDao.updateSyncState(
                localId = localId,
                status = SyncStatus.PENDIENTE,
                backendId = null,
                errorMessage = e.message
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

        evidenceDao.getBySyncStatuses(listOf(SyncStatus.PENDIENTE, SyncStatus.ERROR)).forEach {
            if (syncEvidence(it)) sincronizados++ else errores++
        }

        return SyncRunResult(
            revisados = pendientes.size,
            sincronizados = sincronizados,
            errores = errores
        )
    }


    suspend fun saveEvidence(evidence: BitacoraEvidenceEntity): Long {
        require(bitacoraDao.getById(evidence.bitacoraLocalId) != null) {
            "La evidencia debe estar asociada a una bitácora existente"
        }
        evidence.localFilePath?.let { require(File(it).isFile) { "El archivo local no existe" } }
        return evidenceDao.insertWithRequiredGps(evidence)
    }

    suspend fun getEvidences(localId: Long) = evidenceDao.getForBitacora(localId)

    suspend fun deleteEvidence(localId: Long) {
        val evidence = evidenceDao.getById(localId) ?: return
        require(evidence.syncStatus != SyncStatus.SINCRONIZADO) { "Una evidencia sincronizada no se puede eliminar localmente" }
        evidenceDao.deleteById(localId)
        evidence.localFilePath?.let { File(it).delete() }
    }

    private suspend fun syncEvidence(evidence: BitacoraEvidenceEntity): Boolean {
        val parent = bitacoraDao.getById(evidence.bitacoraLocalId) ?: return false
        val backendId = parent.backendId ?: return false
        val file = evidence.localFilePath?.let(::File)
        if (file == null || !file.isFile) {
            evidenceDao.update(evidence.copy(syncStatus = SyncStatus.ERROR, lastSyncError = "El archivo local no existe"))
            return false
        }
        fun body(value: Any?) = value?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        return try {
            evidenceDao.update(evidence.copy(syncStatus = SyncStatus.SYNCING, syncAttempts = evidence.syncAttempts + 1))
            val response = api.uploadEvidence(
                MultipartBody.Part.createFormData("file", evidence.originalName ?: file.name, file.asRequestBody(evidence.mimeType?.toMediaTypeOrNull())),
                body(backendId)!!, body(evidence.areaId)!!, body(evidence.createdAt / 60000L)!!,
                body(evidence.evidenceType.backendType)!!, body(evidence.clientUuid)!!,
                body(evidence.originalName), body(evidence.mimeType), body(evidence.durationSeconds),
                body(evidence.fileSize), null, body(evidence.latitude), body(evidence.longitude), body(evidence.accuracy)
            )
            evidenceDao.update(evidence.copy(remoteId = response.id_evidencia, syncStatus = SyncStatus.SINCRONIZADO, lastSyncError = null))
            true
        } catch (e: Exception) {
            evidenceDao.update(evidence.copy(syncStatus = SyncStatus.ERROR, lastSyncError = e.message ?: "No fue posible sincronizar"))
            false
        }
    }

    private val EvidenceType.backendType: Int get() = when (this) {
        EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> 1
        EvidenceType.AUDIO -> 2
        EvidenceType.VIDEO -> 3
        EvidenceType.TEXT -> 4
    }

    private fun BitacoraDiariaCreate.ensureClientUuid() =
        if (client_uuid.isNullOrBlank()) {
            copy(client_uuid = java.util.UUID.randomUUID().toString())
        } else {
            this
        }
}

sealed interface CreateBitacoraResult {
    data class Sincronizada(val localId: Long, val bitacora: BitacoraDiariaOut) : CreateBitacoraResult
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
