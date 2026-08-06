package com.cactus.bitacora.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import com.cactus.bitacora.biometric.local.LocalFaceTemplateRepository
import com.cactus.bitacora.data.local.BitacoraDao
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.BitacoraLocalEntity
import com.cactus.bitacora.data.local.BitacoraQueryHeader
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.GpsStatus
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.data.local.toLocalEntity
import com.cactus.bitacora.data.models.AreaByQrIn
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import com.cactus.bitacora.model.EvidenciaTextoCreate
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.ParticipanteOut
import com.cactus.bitacora.model.EmployeeAreaAdminIn
import com.cactus.bitacora.model.ParticipantTypeAdminIn
import com.cactus.bitacora.model.ParticipantTypeStatusIn
import com.cactus.bitacora.model.AdministrativeAreaIn
import com.cactus.bitacora.model.CalendarHolidayUpdateIn
import com.cactus.bitacora.model.CalendarTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaUpdateIn
import com.cactus.bitacora.model.ParticipantOptionOut
import com.cactus.bitacora.util.AppConfig
import retrofit2.HttpException
import com.cactus.bitacora.location.LocationSnapshot
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal fun remoteDeleteAlreadySatisfied(statusCode: Int): Boolean = statusCode == 404

class BitacoraRepository(
    context: Context,
    private val api: BitacoraApi = Api.create()
) {
    private val database = BitacoraDatabase.getInstance(context)
    private val bitacoraDao: BitacoraDao =
        database.bitacoraDao()
    private val evidenceDao = database.evidenceDao()
    private val remoteEvidenceRepository = RemoteEvidenceRepository(api, evidenceDao)
    private val remoteBitacoraRepository = RemoteBitacoraRepository(api, bitacoraDao)
    private val faceTemplateRepository = LocalFaceTemplateRepository(context, api)
    private val referenceCatalogRepository = ReferenceCatalogRepository(context, api)

    private companion object {
        val syncMutex = Mutex()
    }

    suspend fun checkHealth() =
        api.health()

    suspend fun adminParticipantTypes(actor: String) =
        api.getAdminParticipantTypes(
            adminAuthorization(),
            actor.trim(),
            Build.MODEL
        )

    suspend fun createAdminParticipantType(
        actor: String,
        description: String,
        capabilities: List<String>
    ) = api.createAdminParticipantType(
        adminAuthorization(),
        actor.trim(),
        Build.MODEL,
        ParticipantTypeAdminIn(description, capabilities)
    )

    suspend fun updateAdminParticipantType(
        actor: String,
        code: Int,
        description: String,
        capabilities: List<String>
    ) = api.updateAdminParticipantType(
        adminAuthorization(),
        actor.trim(),
        Build.MODEL,
        code,
        ParticipantTypeAdminIn(description, capabilities)
    )

    suspend fun setAdminParticipantTypeStatus(
        actor: String,
        code: Int,
        active: Boolean
    ) = api.setAdminParticipantTypeStatus(
        adminAuthorization(),
        actor.trim(),
        Build.MODEL,
        code,
        ParticipantTypeStatusIn(active)
    )

    suspend fun adminAreaTree(actor: String): List<com.cactus.bitacora.model.AreaTreeNodeOut> {
        val url = "${AppConfig.BASE_URL}admin/areas/arbol"
        val normalizedActor = actor.trim().ifBlank { "administrador-consulta" }
        Log.i("AdminAreaTree", "URL consultada: $url")
        return try {
            val response = api.getAdminAreaTree(
                adminAuthorization(),
                normalizedActor,
                Build.MODEL
            )
            Log.i("AdminAreaTree", "Código HTTP: ${response.code()}")
            if (!response.isSuccessful) throw HttpException(response)
            val areas = response.body()
                ?: throw IOException("El endpoint de áreas respondió sin contenido")
            val ids = areas.mapTo(mutableSetOf()) { it.id_area }
            val roots = areas.count { it.id_padre == null || it.id_padre == 0 }
            val orphanIds = areas
                .filter {
                    it.id_padre != null &&
                        it.id_padre != 0 &&
                        it.id_padre !in ids
                }
                .map { it.id_area }
            Log.i(
                "AdminAreaTree",
                "Áreas recibidas: ${areas.size}; nodos raíz: $roots"
            )
            Log.i("AdminAreaTree", "IDs sin padre válido: $orphanIds")
            areas
        } catch (error: Exception) {
            Log.e(
                "AdminAreaTree",
                "Error HTTP o de deserialización al consultar $url",
                error
            )
            throw error
        }
    }

    suspend fun adminCalendarTree(actor: String): List<CalendarTreeNodeOut> =
        adminMutation(
            action = "CALENDAR_TREE",
            areaId = null,
            endpoint = "${AppConfig.BASE_URL}admin/calendario/arbol"
        ) {
            api.getAdminCalendarTree(
                actor.trim().ifBlank { "administrador-consulta" },
                Build.MODEL
            )
        }.orEmpty()

    suspend fun updateAdminCalendarHoliday(
        actor: String,
        periodId: Long,
        payload: CalendarHolidayUpdateIn
    ): CalendarTreeNodeOut = adminMutation(
        action = "EDIT_CALENDAR_HOLIDAY",
        areaId = null,
        endpoint = "${AppConfig.BASE_URL}admin/calendario/dias/$periodId/festivo"
    ) {
        api.updateAdminCalendarHoliday(
            actor.trim(),
            Build.MODEL,
            periodId,
            payload
        )
    } ?: throw IOException("El servidor no devolvió el día actualizado")

    suspend fun createAdminArea(
        actor: String,
        description: String,
        shortName: String?,
        parentId: Int?
    ) = adminMutation(
        action = "ADD",
        areaId = parentId,
        endpoint = "${AppConfig.BASE_URL}admin/areas"
    ) {
        api.createAdminArea(
            adminAuthorization(),
            actor.trim(),
            Build.MODEL,
            AdministrativeAreaIn(description, shortName, parentId)
        )
    } ?: throw IOException("El servidor no devolvió el área creada")

    suspend fun updateAdminArea(
        actor: String,
        areaId: Int,
        description: String,
        shortName: String?,
        parentId: Int?
    ) = adminMutation(
        action = "EDIT",
        areaId = areaId,
        endpoint = "${AppConfig.BASE_URL}admin/areas/$areaId"
    ) {
        api.updateAdminArea(
            adminAuthorization(),
            actor.trim(),
            Build.MODEL,
            areaId,
            AdministrativeAreaIn(description, shortName, parentId)
        )
    } ?: throw IOException("El servidor no devolvió el área actualizada")

    suspend fun deleteAdminArea(actor: String, areaId: Int) =
        adminMutation<Unit>(
            action = "DEL",
            areaId = areaId,
            endpoint = "${AppConfig.BASE_URL}admin/areas/$areaId",
            requireBody = false
        ) {
            api.deleteAdminArea(
                adminAuthorization(),
                actor.trim(),
                Build.MODEL,
                areaId
            )
        }

    suspend fun createAdminEmployeeArea(
        actor: String,
        payload: EmployeeAreaAdminIn
    ) = api.createAdminEmployeeArea(
        adminAuthorization(),
        actor.trim(),
        Build.MODEL,
        payload
    )

    suspend fun adminEmployeeAreaTree(actor: String): List<EmployeeAreaTreeNodeOut> =
        adminMutation(
            action = "EMPLOYEE_AREA_TREE",
            areaId = null,
            endpoint = "${AppConfig.BASE_URL}admin/empleado-area/tree"
        ) {
            api.getAdminEmployeeAreaTree(
                adminAuthorization(),
                actor.trim().ifBlank { "administrador-consulta" },
                Build.MODEL
            )
        }.orEmpty()

    suspend fun adminParticipantOptions(
        actor: String,
        search: String = ""
    ): List<ParticipantOptionOut> =
        adminMutation(
            action = "PARTICIPANT_OPTIONS",
            areaId = null,
            endpoint = "${AppConfig.BASE_URL}admin/participantes/options"
        ) {
            api.getAdminParticipantOptions(
                adminAuthorization(),
                actor.trim().ifBlank { "administrador-consulta" },
                Build.MODEL,
                search
            )
        }.orEmpty()

    suspend fun updateAdminEmployeeArea(
        actor: String,
        assignmentId: Int,
        payload: EmployeeAreaUpdateIn
    ) = adminMutation(
        action = "EDIT_EMPLOYEE_AREA",
        areaId = payload.id_area,
        endpoint = "${AppConfig.BASE_URL}admin/empleado-area/$assignmentId"
    ) {
        api.updateAdminEmployeeArea(
            adminAuthorization(),
            actor.trim(),
            Build.MODEL,
            assignmentId,
            payload
        )
    } ?: throw IOException("El servidor no devolvió la asignación actualizada")

    suspend fun retireAdminEmployeeArea(actor: String, assignmentId: Int) =
        adminMutation<Unit>(
            action = "DEL_EMPLOYEE_AREA",
            areaId = null,
            endpoint = "${AppConfig.BASE_URL}admin/empleado-area/$assignmentId",
            requireBody = false
        ) {
            api.retireAdminEmployeeArea(
                adminAuthorization(),
                actor.trim(),
                Build.MODEL,
                assignmentId
            )
        }

    private fun adminAuthorization(): String =
        AppConfig.ADMIN_AUTHORIZATION.takeIf(String::isNotBlank)
            ?: throw IllegalStateException(
                "La credencial administrativa no está configurada"
            )

    private suspend fun <T> adminMutation(
        action: String,
        areaId: Int?,
        endpoint: String,
        requireBody: Boolean = true,
        block: suspend () -> retrofit2.Response<T>
    ): T? =
        try {
            Log.i(
                "AdminAreaAction",
                "Acción=$action id=$areaId endpoint=$endpoint"
            )
            val response = block()
            Log.i(
                "AdminAreaAction",
                "Acción=$action id=$areaId HTTP=${response.code()} " +
                    "respuesta=${response.body() ?: "<sin contenido>"}"
            )
            if (!response.isSuccessful) throw HttpException(response)
            response.body().also {
                if (requireBody && it == null) {
                    throw IOException("El servidor respondió sin contenido")
                }
            }
        } catch (error: HttpException) {
            val detail = runCatching {
                JSONObject(error.response()?.errorBody()?.string().orEmpty())
                    .optString("detail")
            }.getOrNull().orEmpty()
            Log.e(
                "AdminAreaAction",
                "Acción=$action id=$areaId HTTP=${error.code()} error=$detail",
                error
            )
            throw IOException(detail.ifBlank { error.message() }, error)
        } catch (error: Exception) {
            Log.e(
                "AdminAreaAction",
                "Acción=$action id=$areaId error de red o deserialización",
                error
            )
            throw error
        }

    suspend fun getAreaByQr(qr: String) =
        referenceCatalogRepository.areaByQr(qr)

    suspend fun getParticipanteByQr(qr: String) =
        referenceCatalogRepository.participantByCode(qr)

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
        referenceCatalogRepository.assignmentsForParticipant(idParticipante)
            .firstOrNull()
            ?: throw CatalogValidationException(
                "El participante no tiene una asignación de área activa"
            )

    suspend fun getAsignacionesActivas(idParticipante: Int) =
        referenceCatalogRepository.assignmentsForParticipant(idParticipante)

    suspend fun getSupervisorForEmployee(idEmpleado: Int): Pair<ParticipanteOut, EmpleadoAreaActivaOut> {
        val response = api.getSupervisorForEmployee(idEmpleado)
        val participant = referenceCatalogRepository.participantById(response.id_supervisor)
        val assignment = referenceCatalogRepository.assignmentsForParticipant(response.id_supervisor)
            .firstOrNull { it.cargo == 3 }
            ?: throw CatalogValidationException(
                "El supervisor asignado no tiene un cargo activo de supervisor"
            )
        return participant to assignment
    }

    suspend fun getAdministrativeAreas() = referenceCatalogRepository.activeAreas()

    suspend fun getObjetosMonitoreoSatelital() =
        runCatching { api.getObjetosMonitoreoSatelital() }.getOrElse {
            listOf(
                com.cactus.bitacora.model.ObjetoMonitoreoSatelitalOut(
                    id_objeto_monitoreo = 1,
                    nombre = "Embalse La Copa",
                    tipo_objeto = "EMBALSE",
                    pais_codigo = "CO",
                    departamento_provincia = "Boyacá",
                    municipio_localidad = "Toca",
                    descripcion = "Objeto piloto para seguimiento satelital mediante Copernicus Sentinel-2."
                )
            )
        }

    suspend fun getReservoirs() = api.getReservoirs()

    suspend fun getReservoirImages(reservoirId: Int) =
        api.getReservoirImages(reservoirId)

    suspend fun downloadSatelliteImage(relativeUrl: String): ByteArray {
        val absoluteUrl = if (relativeUrl.startsWith("http")) {
            relativeUrl
        } else {
            "${AppConfig.BASE_URL.trimEnd('/')}/${relativeUrl.trimStart('/')}"
        }
        return api.downloadSatelliteImage(absoluteUrl).bytes()
    }

    suspend fun getAsignacionParaRol(idParticipante: Int, supervisor: Boolean) =
        referenceCatalogRepository.assignmentForRole(
            idParticipante,
            if (supervisor) CatalogRole.SUPERVISOR else CatalogRole.EMPLOYEE
        )

    suspend fun validarAsignacionArea(idParticipante: Int, idArea: Int) =
        referenceCatalogRepository.requireActiveAssignment(idParticipante, idArea)

    suspend fun syncReferenceCatalogs() = referenceCatalogRepository.syncCatalogs()

    suspend fun getCatalogStatus() = referenceCatalogRepository.status()

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

    suspend fun getLocalBitacoras(): List<BitacoraQueryHeader> {
        val bitacoras = remoteBitacoraRepository.refreshAndGet()
        bitacoras.forEach { header ->
            header.bitacora.backendId?.let { remoteId ->
                remoteEvidenceRepository.refreshAndGet(header.bitacora.localId, remoteId)
            }
        }
        return bitacoraDao.getAllForQuery()
    }

    suspend fun getSyncSummary(): SyncSummary {
        val catalogs = referenceCatalogRepository.status()
        return SyncSummary(
        bitacorasPendientes = bitacoraDao.countPending(),
        evidenciasPendientes = evidenceDao.countPending(),
        enrolamientosPendientes = faceTemplateRepository.pendingCount(),
        sincronizados = bitacoraDao.countByStatus(SyncStatus.SINCRONIZADO),
        errores = bitacoraDao.countErrors() + evidenceDao.countErrors() +
            faceTemplateRepository.errorCount(),
        catalogParticipants = catalogs.participants,
        catalogAreas = catalogs.areas,
        catalogAssignments = catalogs.assignments,
        catalogLastSyncMillis = catalogs.lastSuccessfulSyncMillis,
        catalogLastError = catalogs.lastError
        )
    }

    suspend fun sincronizarPendientes(): SyncRunResult =
        syncMutex.withLock { sincronizarPendientesInternal() }

    private suspend fun sincronizarPendientesInternal(): SyncRunResult {
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
        val mensajes = mutableListOf<String>()
        val catalogSync = referenceCatalogRepository.syncCatalogs()
        if (!catalogSync.success) {
            errores++
            if (catalogSync.retryable) reintentables++
            catalogSync.error?.let { mensajes += "Catálogos: $it" }
        }

        pendientes.forEach { local ->
            try {
                bitacoraDao.incrementSyncAttempts(local.localId)
                Log.i(
                    SYNC_TAG,
                    "bitacora localUuid=${local.clientUuid} localId=${local.localId} " +
                        "state=${local.syncStatus} endpoint=POST bitacora_diaria"
                )
                val response = api.crearBitacoraDiaria(local.toCreateRequest())
                bitacoraDao.updateSyncState(
                    localId = local.localId,
                    status = SyncStatus.SINCRONIZADO,
                    backendId = response.id_bitacora,
                    errorMessage = null
                )
                Log.i(
                    SYNC_TAG,
                    "bitacora localUuid=${local.clientUuid} localId=${local.localId} " +
                        "serverId=${response.id_bitacora} state=SINCRONIZADO http=200"
                )
                sincronizados++
            } catch (e: Exception) {
                val retryable = e.isRetryableSyncError()
                val diagnostic = e.safeSyncDiagnostic()
                bitacoraDao.updateSyncState(
                    localId = local.localId,
                    status = if (retryable) local.syncStatus.pendingEquivalent()
                    else SyncStatus.ERROR,
                    backendId = local.backendId,
                    errorMessage = diagnostic
                )
                Log.e(
                    SYNC_TAG,
                    "bitacora localUuid=${local.clientUuid} localId=${local.localId} " +
                        "state=ERROR $diagnostic"
                )
                mensajes += "Bitácora ${local.localId}: $diagnostic"
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
                result.message?.let(mensajes::add)
            }
        }
        val faceSync = faceTemplateRepository.syncWithCentral()
        sincronizados += faceSync.uploaded + faceSync.downloaded
        errores += faceSync.errors
        reintentables += faceSync.retryableErrors
        mensajes += faceSync.errorMessages.map { "Enrolamiento: $it" }

        runCatching { enforceLocalEvidenceRetention() }
            .onFailure { Log.w(RETENTION_TAG, "No se pudo aplicar la retención local", it) }

        return SyncRunResult(
            revisados = 1 + pendientes.size + pendingEvidences.size +
                faceSync.uploaded + faceSync.downloaded + faceSync.errors,
            sincronizados = sincronizados,
            errores = errores,
            erroresReintentables = reintentables,
            mensajes = mensajes.distinct()
        )
    }

    private suspend fun enforceLocalEvidenceRetention(now: Long = System.currentTimeMillis()) {
        val evidenceWithLocalFile = evidenceDao.getWithLocalFile()
        val existingFiles = evidenceWithLocalFile.mapNotNull { evidence ->
            val file = evidence.localFilePath?.let(::File)
            if (file?.isFile == true) evidence to file else null
        }
        val totalLocalBytes = existingFiles.sumOf { (_, file) -> file.length() }
        val victims = selectLocalEvidenceRetentionVictims(
            candidates = existingFiles.map { (evidence, file) ->
                LocalEvidenceRetentionCandidate(
                    localId = evidence.localId,
                    createdAt = evidence.createdAt,
                    bytes = file.length(),
                    synchronized = evidence.syncStatus == SyncStatus.SINCRONIZADO,
                    hasRemoteCopy = evidence.remoteId != null
                )
            },
            totalLocalBytes = totalLocalBytes,
            now = now
        ).toSet()

        existingFiles
            .filter { (evidence, _) -> evidence.localId in victims }
            .forEach { (evidence, file) ->
                val releasedBytes = file.length()
                if (file.delete() || !file.exists()) {
                    evidenceDao.clearSyncedLocalFilePath(evidence.localId)
                    Log.i(
                        RETENTION_TAG,
                        "Archivo local liberado localId=${evidence.localId} bytes=$releasedBytes"
                    )
                } else {
                    Log.w(RETENTION_TAG, "No se pudo liberar ${file.absolutePath}")
                }
            }
    }


    suspend fun saveEvidence(evidence: BitacoraEvidenceEntity): Long {
        require(bitacoraDao.getById(evidence.bitacoraLocalId) != null) {
            "La evidencia debe estar asociada a una bitácora existente"
        }
        val file = evidence.localFilePath?.let(::File)
        file?.let {
            require(it.isFile) { "El archivo local no existe" }
            require(it.length() > 0L) { "El archivo local está vacío" }
        }
        val prepared = evidence.copy(
            fileSize = evidence.fileSize ?: file?.length(),
            fileHash = evidence.fileHash ?: file?.sha256()
        )
        val localId = if (prepared.evidenceType == EvidenceType.TEXT) {
            evidenceDao.insert(prepared)
        } else {
            evidenceDao.insertWithRequiredGps(prepared)
        }
        Log.i(
            SYNC_TAG,
            "evidence localUuid=${prepared.clientUuid} localId=$localId " +
                "bitacoraLocalId=${prepared.bitacoraLocalId} name=${file?.name} " +
                "size=${prepared.fileSize} state=${prepared.syncStatus}"
        )
        return localId
    }

    suspend fun getEvidences(localId: Long): List<BitacoraEvidenceEntity> {
        val bitacora = bitacoraDao.getById(localId)
        return remoteEvidenceRepository.refreshAndGet(localId, bitacora?.backendId)
    }

    suspend fun deleteEvidence(localId: Long) {
        val evidence = evidenceDao.getById(localId) ?: return
        evidence.remoteId?.let { remoteId ->
            try {
                api.deleteEvidence(remoteId)
            } catch (error: HttpException) {
                if (!remoteDeleteAlreadySatisfied(error.code())) throw error
                Log.i(
                    SYNC_TAG,
                    "evidence remoteId=$remoteId already absent; deleting local copy"
                )
            }
        }
        evidence.localFilePath?.let { path ->
            val file = File(path)
            check(!file.exists() || file.delete()) { "No se pudo eliminar el archivo local" }
        }
        evidenceDao.deleteById(localId)
    }

    suspend fun deleteBitacora(localId: Long) {
        val bitacora = bitacoraDao.getById(localId) ?: return
        val evidences = evidenceDao.getAllForBitacora(localId, bitacora.backendId)
        bitacora.backendId?.let { backendId ->
            try {
                api.deleteBitacora(backendId)
            } catch (error: HttpException) {
                if (!remoteDeleteAlreadySatisfied(error.code())) throw error
                Log.i(
                    SYNC_TAG,
                    "bitacora backendId=$backendId already absent; deleting local copy"
                )
            }
        }
        evidences.forEach { evidence ->
            evidence.localFilePath?.let { path ->
                val file = File(path)
                check(!file.exists() || file.delete()) {
                    "No se pudo eliminar el archivo local ${file.name}"
                }
            }
        }
        database.withTransaction {
            evidenceDao.deleteForBitacora(localId, bitacora.backendId)
            bitacoraDao.deleteById(localId)
        }
    }

    suspend fun deleteOldestBitacoras(limit: Int = 10): Int {
        require(limit > 0) { "La cantidad debe ser mayor que cero" }
        val oldest = bitacoraDao.getAllForQuery()
            .map { it.bitacora }
            .sortedWith(compareBy<BitacoraLocalEntity> { it.createdAtMillis }.thenBy { it.localId })
            .take(limit)
        oldest.forEach { deleteBitacora(it.localId) }
        return oldest.size
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
            return SyncItemResult(false, false, "Evidencia ${evidence.localId}: la bitácora local asociada no existe")
        }
        val backendId = parent.backendId
        if (backendId == null) {
            evidenceDao.update(
                evidence.copy(
                    syncStatus = SyncStatus.ERROR,
                    lastSyncError = "La bitácora aún no tiene identificador del servidor"
                )
            )
            return SyncItemResult(
                false,
                false,
                "Evidencia ${evidence.localId}: la bitácora aún no tiene identificador del servidor"
            )
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
                SyncItemResult(false, retryable, error.safeSyncDiagnostic())
            }
        }
        if (evidence.evidenceType == EvidenceType.TEXT) {
            val content = evidence.textContent?.trim().orEmpty()
            if (content.isEmpty()) {
                evidenceDao.update(
                    evidence.copy(syncStatus = SyncStatus.ERROR, lastSyncError = "El texto está vacío")
                )
                return SyncItemResult(false, false, "Evidencia ${evidence.localId}: el texto está vacío")
            }
            return try {
                evidenceDao.update(evidence.copy(syncAttempts = evidence.syncAttempts + 1))
                val response = api.createTextEvidence(
                    EvidenciaTextoCreate(
                        id_bitacora = backendId,
                        id_area = evidence.areaId,
                        ts_in_min = (evidence.createdAt / 60000L).toInt(),
                        contenido_texto = content,
                        uuid_cliente = evidence.clientUuid
                    )
                )
                evidenceDao.update(
                    evidence.copy(
                        bitacoraServerId = backendId,
                        remoteId = response.id_evidencia,
                        syncStatus = SyncStatus.SINCRONIZADO,
                        lastSyncError = null
                    )
                )
                SyncItemResult(true, false)
            } catch (error: Exception) {
                val retryable = error.isRetryableSyncError()
                val diagnostic = error.safeSyncDiagnostic()
                evidenceDao.update(
                    evidence.copy(
                        syncStatus = if (retryable) evidence.syncStatus.pendingEquivalent() else SyncStatus.ERROR,
                        syncAttempts = evidence.syncAttempts + 1,
                        lastSyncError = diagnostic
                    )
                )
                SyncItemResult(false, retryable, diagnostic)
            }
        }
        if (file == null || !file.isFile) {
            evidenceDao.update(evidence.copy(syncStatus = SyncStatus.ERROR, lastSyncError = "El archivo local no existe"))
            return SyncItemResult(false, false, "Evidencia ${evidence.localId}: el archivo local no existe")
        }
        fun body(value: Any?) = value?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        return try {
            evidenceDao.update(evidence.copy(syncAttempts = evidence.syncAttempts + 1))
            val upload = evidenceUploadMetadata(
                file,
                evidence.evidenceType,
                evidence.originalName,
                evidence.mimeType
            )
            Log.i(
                SYNC_TAG,
                "evidence multipart filename=${upload.filename} content_type=${upload.mimeType} " +
                    "id_tipo_evidencia=${upload.backendType} size=${file.length()}"
            )
            val response = api.uploadEvidence(
                MultipartBody.Part.createFormData("file", upload.filename, file.asRequestBody(upload.mimeType.toMediaTypeOrNull())),
                body(backendId)!!, body(evidence.areaId)!!, body(evidence.createdAt / 60000L)!!,
                body(upload.backendType)!!, body(evidence.clientUuid)!!,
                body(upload.filename), body(evidence.fileHash),
                body(upload.mimeType), body(evidence.durationSeconds),
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
            SyncItemResult(false, retryable, "Evidencia ${evidence.localId}: $diagnostic")
        }
    }

    private fun BitacoraDiariaCreate.ensureClientUuid() =
        if (client_uuid.isNullOrBlank()) {
            copy(client_uuid = java.util.UUID.randomUUID().toString())
        } else {
            this
        }
}

internal data class SyncItemResult(
    val success: Boolean,
    val retryable: Boolean,
    val message: String? = null
)

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
private const val RETENTION_TAG = "LocalEvidenceRetention"

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
        val serverDetail = response()?.errorBody()?.string()
            ?.replace(Regex("\\s+"), " ")
            ?.take(300)
        "http=${code()} message=${serverDetail?.takeIf { it.isNotBlank() } ?: message().take(200)}"
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
    val errores: Int,
    val catalogParticipants: Int = 0,
    val catalogAreas: Int = 0,
    val catalogAssignments: Int = 0,
    val catalogLastSyncMillis: Long? = null,
    val catalogLastError: String? = null
) {
    val pendientes: Int
        get() = bitacorasPendientes + evidenciasPendientes + enrolamientosPendientes
}

data class SyncRunResult(
    val revisados: Int,
    val sincronizados: Int,
    val errores: Int,
    val erroresReintentables: Int = 0,
    val mensajes: List<String> = emptyList()
)
