package com.cactus.bitacora.data

import android.content.Context
import android.util.Log
import com.cactus.bitacora.biometric.local.LocalFaceTemplateRepository
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
import java.io.IOException
import java.security.MessageDigest
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
    private val faceTemplateRepository = LocalFaceTemplateRepository(context, api)

    suspend fun checkHealth() =
        api.health()

    suspend fun getAreaByQr(qr: String) =
        api.getAreaByQr(AreaByQrIn(qr))

    suspend fun getParticipanteByQr(qr: String) =
        api.getParticipanteByQr(normalizeParticipantQuery(qr))

    suspend fun searchParticipantes(query: String): ParticipantSearchResult {
        val normalized = normalizeParticipantQuery(query)
        if (normalized.isBlank()) {
            return ParticipantSearchResult(query, normalized, "API participante", emptyList())
        }

        var participants: List<com.cactus.bitacora.model.ParticipanteOut> = emptyList()
        var source = "API participante/search"
        try {
            val searchResults = api.searchParticipantes(normalized)
            if (searchResults.isNotEmpty()) {
                participants = searchResults
                source = "API participante/search"
            } else {
                participants = findParticipantByExactCode(normalized)
                source = "API participante/search + participante/by_qr"
            }
        } catch (exception: HttpException) {
            if (exception.code() != 404) throw exception
            participants = findParticipantByExactCode(normalized)
            source = "API participante/by_qr (fallback: search no desplegado)"
        }

        Log.i(
            PARTICIPANT_SEARCH_TAG,
            "recibido='$query', normalizado='$normalized', fuente='$source', " +
                "resultados=${participants.size}"
        )
        return ParticipantSearchResult(query, normalized, source, participants)
    }

    private suspend fun findParticipantByExactCode(
        normalizedCode: String
    ): List<com.cactus.bitacora.model.ParticipanteOut> =
        try {
            listOf(api.getParticipanteByQr(normalizedCode))
        } catch (exception: HttpException) {
            if (exception.code() == 404) emptyList() else throw exception
        }

    suspend fun getAsignacionActiva(idParticipante: Int) =
        api.getAsignacionActiva(idParticipante)

    suspend fun getAsignacionesActivas(idParticipante: Int) =
        try {
            api.getAsignacionesActivas(idParticipante)
        } catch (exception: HttpException) {
            if (exception.code() == 404) {
                listOf(api.getAsignacionActiva(idParticipante))
            } else {
                throw exception
            }
        }

    suspend fun crearBitacoraDiaria(
        request: BitacoraDiariaCreate,
        openLocation: LocationSnapshot? = null,
        closeLocation: LocationSnapshot? = null
    ): CreateBitacoraResult {
        val requestWithUuid = request.ensureClientUuid()
        val localId = bitacoraDao.insert(
            requestWithUuid.toLocalEntity(
                syncStatus = SyncStatus.PENDIENTE_CREAR,
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
            val status = if (e.isRetryableSyncError()) {
                SyncStatus.PENDIENTE_CREAR
            } else {
                SyncStatus.ERROR
            }
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
                status = SyncStatus.PENDIENTE_CREAR,
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
        bitacorasPendientes = bitacoraDao.countPending(),
        evidenciasPendientes = evidenceDao.countPending(),
        enrolamientosPendientes = faceTemplateRepository.pendingCount(),
        sincronizados = bitacoraDao.countByStatus(SyncStatus.SINCRONIZADO),
        errores = bitacoraDao.countErrors() + evidenceDao.countErrors() +
            faceTemplateRepository.errorCount()
    )

    suspend fun sincronizarPendientes(): SyncRunResult {
        val pendientes = bitacoraDao.getByStatuses(
            listOf(
                SyncStatus.PENDIENTE_CREAR,
                SyncStatus.PENDIENTE_ACTUALIZAR,
                SyncStatus.PENDIENTE_ELIMINAR,
                SyncStatus.ERROR
            )
        )
        var sincronizados = 0
        var errores = 0
        var reintentables = 0

        pendientes.forEach { local ->
            try {
                bitacoraDao.incrementSyncAttempts(local.localId)
                val response = api.crearBitacoraDiaria(local.toCreateRequest())
                bitacoraDao.updateSyncState(
                    localId = local.localId,
                    status = SyncStatus.SINCRONIZADO,
                    backendId = response.id_bitacora,
                    errorMessage = null
                )
                sincronizados++
            } catch (e: Exception) {
                val retryable = e.isRetryableSyncError()
                bitacoraDao.updateSyncState(
                    localId = local.localId,
                    status = if (retryable) local.syncStatus.pendingEquivalent()
                    else SyncStatus.ERROR,
                    backendId = local.backendId,
                    errorMessage = e.message ?: "No fue posible sincronizar"
                )
                errores++
                if (retryable) reintentables++
            }
        }

        val pendingEvidences = evidenceDao.getBySyncStatuses(
            listOf(
                SyncStatus.PENDIENTE_CREAR,
                SyncStatus.PENDIENTE_ACTUALIZAR,
                SyncStatus.PENDIENTE_ELIMINAR,
                SyncStatus.ERROR
            )
        )
        pendingEvidences.forEach {
            val result = syncEvidence(it)
            if (result.success) sincronizados++ else {
                errores++
                if (result.retryable) reintentables++
            }
        }
        val faceSync = faceTemplateRepository.syncWithCentral()
        sincronizados += faceSync.uploaded + faceSync.downloaded
        errores += faceSync.errors
        reintentables += faceSync.errors

        return SyncRunResult(
            revisados = pendientes.size + pendingEvidences.size +
                faceSync.uploaded + faceSync.downloaded + faceSync.errors,
            sincronizados = sincronizados,
            errores = errores,
            erroresReintentables = reintentables
        )
    }


    suspend fun saveEvidence(evidence: BitacoraEvidenceEntity): Long {
        require(bitacoraDao.getById(evidence.bitacoraLocalId) != null) {
            "La evidencia debe estar asociada a una bitácora existente"
        }
        val file = evidence.localFilePath?.let(::File)
        file?.let { require(it.isFile) { "El archivo local no existe" } }
        val prepared = evidence.copy(
            fileSize = evidence.fileSize ?: file?.length(),
            fileHash = evidence.fileHash ?: file?.sha256()
        )
        val localId = evidenceDao.insertWithRequiredGps(prepared)
        Log.i(
            SYNC_TAG,
            "evidence localUuid=${prepared.clientUuid} localId=$localId " +
                "bitacoraLocalId=${prepared.bitacoraLocalId} name=${file?.name} " +
                "size=${prepared.fileSize} state=${prepared.syncStatus}"
        )
        return localId
    }

    suspend fun getEvidences(localId: Long) = evidenceDao.getForBitacora(localId)

    suspend fun deleteEvidence(localId: Long) {
        val evidence = evidenceDao.getById(localId) ?: return
        if (evidence.remoteId == null) {
            evidenceDao.deleteById(localId)
            evidence.localFilePath?.let { File(it).delete() }
        } else {
            evidenceDao.markPendingDelete(localId)
        }
    }

    private suspend fun syncEvidence(evidence: BitacoraEvidenceEntity): SyncItemResult {
        val parent = bitacoraDao.getById(evidence.bitacoraLocalId)
        if (parent == null) {
            evidenceDao.update(
                evidence.copy(
                    syncStatus = SyncStatus.ERROR,
                    lastSyncError = "La bitácora local asociada no existe"
                )
            )
            return SyncItemResult(false, false)
        }
        val backendId = parent.backendId
        if (backendId == null) {
            evidenceDao.update(
                evidence.copy(
                    syncStatus = SyncStatus.ERROR,
                    lastSyncError = "La bitácora aún no tiene identificador del servidor"
                )
            )
            return SyncItemResult(false, false)
        }
        val file = evidence.localFilePath?.let(::File)
        if (evidence.syncStatus == SyncStatus.PENDIENTE_ELIMINAR) {
            val remoteId = evidence.remoteId
            if (remoteId == null) {
                evidenceDao.deleteById(evidence.localId)
                evidence.localFilePath?.let { File(it).delete() }
                return SyncItemResult(true, false)
            }
            return try {
                api.deleteEvidence(remoteId)
                evidenceDao.deleteById(evidence.localId)
                evidence.localFilePath?.let { File(it).delete() }
                SyncItemResult(true, false)
            } catch (error: Exception) {
                val retryable = error.isRetryableSyncError()
                evidenceDao.update(
                    evidence.copy(
                        syncStatus = if (retryable) SyncStatus.PENDIENTE_ELIMINAR
                        else SyncStatus.ERROR,
                        syncAttempts = evidence.syncAttempts + 1,
                        lastSyncError = error.message
                    )
                )
                SyncItemResult(false, retryable)
            }
        }
        if (file == null || !file.isFile) {
            evidenceDao.update(evidence.copy(syncStatus = SyncStatus.ERROR, lastSyncError = "El archivo local no existe"))
            return SyncItemResult(false, false)
        }
        fun body(value: Any?) = value?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        return try {
            evidenceDao.update(evidence.copy(syncAttempts = evidence.syncAttempts + 1))
            val response = api.uploadEvidence(
                MultipartBody.Part.createFormData("file", evidence.originalName ?: file.name, file.asRequestBody(evidence.mimeType?.toMediaTypeOrNull())),
                body(backendId)!!, body(evidence.areaId)!!, body(evidence.createdAt / 60000L)!!,
                body(evidence.evidenceType.backendType)!!, body(evidence.clientUuid)!!,
                body(evidence.originalName), body(evidence.fileHash),
                body(evidence.mimeType), body(evidence.durationSeconds),
                body(evidence.fileSize), null, body(evidence.latitude), body(evidence.longitude), body(evidence.accuracy)
            )
            evidenceDao.update(evidence.copy(remoteId = response.id_evidencia, syncStatus = SyncStatus.SINCRONIZADO, lastSyncError = null))
            Log.i(
                SYNC_TAG,
                "evidence localUuid=${evidence.clientUuid} bitacoraServerId=$backendId " +
                    "name=${file.name} size=${file.length()} http=201 " +
                    "serverId=${response.id_evidencia} state=SINCRONIZADO"
            )
            SyncItemResult(true, false)
        } catch (e: Exception) {
            val retryable = e.isRetryableSyncError()
            val diagnostic = e.safeSyncDiagnostic()
            Log.e(
                SYNC_TAG,
                "evidence localUuid=${evidence.clientUuid} bitacoraServerId=$backendId " +
                    "name=${file.name} size=${file.length()} $diagnostic"
            )
            evidenceDao.update(
                evidence.copy(
                    syncStatus = if (retryable) evidence.syncStatus.pendingEquivalent()
                    else SyncStatus.ERROR,
                    syncAttempts = evidence.syncAttempts + 1,
                    lastSyncError = diagnostic
                )
            )
            SyncItemResult(false, retryable)
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

internal data class SyncItemResult(val success: Boolean, val retryable: Boolean)

internal fun Throwable.isRetryableSyncError(): Boolean = when (this) {
    is IOException -> true
    is HttpException -> code() == 408 || code() == 429 || code() >= 500
    else -> true
}

private fun SyncStatus.pendingEquivalent(): SyncStatus = when (this) {
    SyncStatus.PENDIENTE_ACTUALIZAR -> SyncStatus.PENDIENTE_ACTUALIZAR
    SyncStatus.PENDIENTE_ELIMINAR -> SyncStatus.PENDIENTE_ELIMINAR
    else -> SyncStatus.PENDIENTE_CREAR
}

private const val PARTICIPANT_SEARCH_TAG = "ParticipantSearch"
private const val SYNC_TAG = "OfflineSync"

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun Throwable.safeSyncDiagnostic(): String =
    if (this is HttpException) {
        "http=${code()} message=${message().take(200)}"
    } else {
        "http=NO_DISPONIBLE message=${message?.take(200) ?: javaClass.simpleName}"
    }

sealed interface CreateBitacoraResult {
    data class Sincronizada(val localId: Long, val bitacora: BitacoraDiariaOut) : CreateBitacoraResult
    data class Pendiente(val localId: Long, val message: String) : CreateBitacoraResult
}

data class SyncSummary(
    val bitacorasPendientes: Int,
    val evidenciasPendientes: Int,
    val enrolamientosPendientes: Int,
    val sincronizados: Int,
    val errores: Int
) {
    val pendientes: Int
        get() = bitacorasPendientes + evidenciasPendientes + enrolamientosPendientes
}

data class SyncRunResult(
    val revisados: Int,
    val sincronizados: Int,
    val errores: Int,
    val erroresReintentables: Int = 0
)
