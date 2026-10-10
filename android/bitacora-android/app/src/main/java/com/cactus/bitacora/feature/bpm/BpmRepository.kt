package com.cactus.bitacora.feature.bpm

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.data.local.BpmCaseLocalEntity
import com.cactus.bitacora.data.local.BpmDefinitionLocalEntity
import com.cactus.bitacora.data.local.BpmEvidenceLocalEntity
import com.cactus.bitacora.data.local.BpmLocalDao
import com.cactus.bitacora.data.local.BpmOperationLocalEntity
import com.cactus.bitacora.data.local.BpmSyncState
import com.cactus.bitacora.data.local.BpmTaskLocalEntity
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.time.Instant
import android.util.Base64
import java.util.UUID
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal data class BpmDefinitionRemote(
    val id_bpm_proceso: Long,
    val nombre: String,
    val version: Int,
    val descripcion: String?,
    val activo: Boolean,
    val sha256: String,
    val definition: Map<String, Any?>
)

internal data class BpmTaskRemote(
    val id_actividad: Long,
    val id_caso: Long,
    val id_bpm_proceso: Long,
    val id_etapa: Int,
    val asunto: String,
    val estado: String,
    val revision: Int,
    val resultado: String?,
    val observaciones: String?,
    val fecha_vencimiento: String?,
    val etapa_nombre: String,
    val responsable_codigo: String?,
    val task_client_uuid: String?,
    val case_client_uuid: String?,
    val id_participante_afectado: String,
    val id_participante_creador: String,
    val caso_estado: String,
    val caso_revision: Int,
    val definicion_sha256: String,
    val origen_tipo: String,
    val origen_id: String?,
    val fecha_apertura: String,
    val fecha_cierre: String?
)

internal data class BpmEvidenceRemoteIn(
    val client_uuid: String,
    val filename: String,
    val mime_type: String,
    val sha256: String,
    val content_base64: String,
    val captured_at: String
)

internal data class BpmOperationRemoteIn(
    val operation_uuid: String,
    val operation_type: String,
    val actor_code: String,
    val device: String,
    val captured_at: String,
    val base_revision: Int? = null,
    val process_id: Long? = null,
    val definition_sha256: String? = null,
    val case_id: Long? = null,
    val task_id: Long? = null,
    val case_client_uuid: String? = null,
    val task_client_uuid: String? = null,
    val successor_client_uuid: String? = null,
    val subject: String? = null,
    val affected_code: String? = null,
    val source_type: String? = null,
    val source_id: String? = null,
    val source_table: String? = null,
    val responsible_id: Int? = null,
    val result: String? = null,
    val observations: String? = null,
    val reason: String? = null,
    val evidences: List<BpmEvidenceRemoteIn> = emptyList(),
    val dependency_uuid: String? = null
)

internal data class BpmSyncRemoteIn(val operations: List<BpmOperationRemoteIn>)
internal data class BpmOperationRemoteOut(
    val operation_uuid: String,
    val status: String,
    val case_id: Long?,
    val task_id: Long?,
    val next_task_id: Long?,
    val case_client_uuid: String?,
    val task_client_uuid: String?,
    val successor_client_uuid: String?,
    val case_state: String?,
    val task_state: String?,
    val case_revision: Int?,
    val task_revision: Int?,
    val message: String?,
    val server_time: String
)
internal data class BpmSyncRemoteOut(val results: List<BpmOperationRemoteOut>)

internal interface BpmApi {
    @GET("bpm/definitions") suspend fun definitions(): List<BpmDefinitionRemote>
    @GET("bpm/tasks") suspend fun tasks(@Query("actor") actor: String): List<BpmTaskRemote>
    @POST("bpm/sync") suspend fun sync(@Body payload: BpmSyncRemoteIn): BpmSyncRemoteOut
    @GET("bpm/cases/{id}") suspend fun caseDetail(@Path("id") id: Long, @Query("actor") actor: String): Map<String, Any?>
}

internal enum class DependencyDisposition { SEND, WAIT, BLOCK }

internal fun dependencyDisposition(state: String?): DependencyDisposition = when (state) {
    null, BpmSyncState.CONFIRMED -> DependencyDisposition.SEND
    BpmSyncState.CONFLICT, BpmSyncState.REJECTED -> DependencyDisposition.BLOCK
    else -> DependencyDisposition.WAIT
}

internal data class BpmSyncSummary(val reviewed: Int, val confirmed: Int, val conflicts: Int, val rejected: Int, val retryable: Int = 0)

internal data class BpmEvidenceCapture(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray
)

internal class BpmRepository(
    private val database: BitacoraDatabase,
    private val dao: BpmLocalDao,
    private val api: BpmApi,
    private val deviceName: String
) {
    constructor(context: Context) : this(
        BitacoraDatabase.getInstance(context),
        BitacoraDatabase.getInstance(context).bpmLocalDao(),
        NetworkClient.createService(BpmApi::class.java),
        "${Build.MANUFACTURER}-${Build.MODEL}"
    )

    private val gson = Gson()

    suspend fun definitions(): List<BpmDefinitionLocalEntity> = dao.definitions()
    suspend fun tasks(actor: String): List<BpmTaskLocalEntity> = dao.tasks(actor.trim().uppercase())
    suspend fun cases(actor: String): List<BpmCaseLocalEntity> = dao.cases(actor.trim().uppercase())

    suspend fun refresh(actor: String) {
        val now = System.currentTimeMillis()
        val definitions = api.definitions().map {
            BpmDefinitionLocalEntity(it.id_bpm_proceso, it.nombre, it.version, it.descripcion,
                it.sha256, gson.toJson(it.definition), it.activo, now)
        }
        dao.putDefinitions(definitions)
        val remoteTasks = api.tasks(actor.trim().uppercase())
        remoteTasks.distinctBy { it.id_caso }.forEach {
            dao.putCase(BpmCaseLocalEntity(
                clientUuid = it.case_client_uuid ?: "server-case-${it.id_caso}",
                remoteId = it.id_caso,
                processId = it.id_bpm_proceso,
                definitionSha256 = it.definicion_sha256,
                subject = it.asunto,
                affectedCode = it.id_participante_afectado,
                creatorCode = it.id_participante_creador,
                sourceType = it.origen_tipo,
                sourceId = it.origen_id,
                state = it.caso_estado,
                revision = it.caso_revision,
                capturedAt = it.fecha_apertura,
                confirmedAt = it.fecha_cierre ?: it.fecha_apertura,
                syncState = BpmSyncState.CONFIRMED,
                lastError = null
            ))
        }
        dao.putTasks(remoteTasks.map {
            BpmTaskLocalEntity(
                clientUuid = it.task_client_uuid ?: "server-task-${it.id_actividad}",
                remoteId = it.id_actividad,
                caseClientUuid = it.case_client_uuid ?: "server-case-${it.id_caso}",
                processId = it.id_bpm_proceso,
                stageId = it.id_etapa,
                stageName = it.etapa_nombre,
                responsibleCode = it.responsable_codigo ?: actor.trim().uppercase(),
                state = it.estado,
                revision = it.revision,
                result = it.resultado,
                observations = it.observaciones,
                dueAt = it.fecha_vencimiento,
                provisional = false,
                syncState = BpmSyncState.CONFIRMED,
                lastError = null
            )
        })
    }

    suspend fun queueOpenCase(
        actor: String,
        definition: BpmDefinitionLocalEntity,
        subject: String,
        affectedCode: String,
        sourceType: String = "MANUAL",
        sourceId: String? = null,
        sourceTable: String? = null
    ): String = database.withTransaction {
        require(subject.trim().isNotEmpty() && subject.length <= 200) { "El asunto es obligatorio y admite máximo 200 caracteres" }
        val operationUuid = UUID.randomUUID().toString()
        val caseUuid = UUID.randomUUID().toString()
        val taskUuid = UUID.randomUUID().toString()
        val captured = Instant.now().toString()
        val initial = initialStage(definition.definitionJson)
        val payload = BpmOperationRemoteIn(
            operation_uuid = operationUuid, operation_type = "OPEN_CASE",
            actor_code = actor.trim().uppercase(), device = deviceName, captured_at = captured,
            process_id = definition.processId, definition_sha256 = definition.sha256,
            case_client_uuid = caseUuid, task_client_uuid = taskUuid,
            subject = subject.trim(), affected_code = affectedCode.trim().uppercase(),
            source_type = sourceType, source_id = sourceId, source_table = sourceTable
        )
        dao.putCase(BpmCaseLocalEntity(caseUuid, null, definition.processId, definition.sha256,
            subject.trim(), affectedCode.trim().uppercase(), actor.trim().uppercase(), sourceType,
            sourceId, "ABIERTO", 1, captured, null, BpmSyncState.LOCAL_PENDING, null))
        dao.putTasks(listOf(BpmTaskLocalEntity(taskUuid, null, caseUuid, definition.processId,
            initial.first, initial.second, affectedCode.trim().uppercase(), "PENDIENTE", 1,
            null, null, null, true, BpmSyncState.LOCAL_PENDING, null)))
        dao.putOperation(operationEntity(payload, caseUuid, taskUuid, null, null))
        caseUuid
    }

    suspend fun queueStart(actor: String, task: BpmTaskLocalEntity): String = database.withTransaction {
        require(task.state == "PENDIENTE") { "Solo una tarea pendiente puede iniciarse" }
        val uuid = UUID.randomUUID().toString()
        val case = requireNotNull(dao.caseByUuid(task.caseClientUuid)) { "No existe el caso local" }
        val payload = BpmOperationRemoteIn(uuid, "START_TASK", actor.trim().uppercase(), deviceName,
            Instant.now().toString(), task.revision, task.processId, case.definitionSha256,
            case.remoteId, task.remoteId, case.clientUuid, task.clientUuid)
        dao.updateLocalTask(task.clientUuid, "EN_EJECUCION", task.revision + 1, null, null,
            true, BpmSyncState.LOCAL_PENDING, null)
        dao.putOperation(operationEntity(payload, case.clientUuid, task.clientUuid, null, latestDependency(case.clientUuid)))
        uuid
    }

    suspend fun queueComplete(
        actor: String,
        task: BpmTaskLocalEntity,
        result: String,
        observations: String,
        supports: List<BpmEvidenceCapture>
    ): String = database.withTransaction {
        require(task.state == "EN_EJECUCION") { "La tarea debe estar en ejecución" }
        require(result.isNotBlank() && result.length <= 100) { "Seleccione un resultado permitido" }
        val case = requireNotNull(dao.caseByUuid(task.caseClientUuid)) { "No existe el caso local" }
        val definition = requireNotNull(dao.definitions().firstOrNull { it.processId == task.processId }) { "Falta la definición local" }
        val next = nextStage(definition.definitionJson, task.stageId, result.trim())
        val successorUuid = next?.let { UUID.randomUUID().toString() }
        require(supports.size <= 20) { "Se permiten máximo 20 soportes" }
        val evidences = supports.map { support ->
            require(support.bytes.isNotEmpty()) { "El soporte ${support.filename} está vacío" }
            BpmEvidenceLocalEntity(UUID.randomUUID().toString(), case.clientUuid, task.clientUuid,
                support.filename.take(255), support.mimeType.take(100), sha256(support.bytes),
                Base64.encodeToString(support.bytes, Base64.NO_WRAP), Instant.now().toString(),
                BpmSyncState.LOCAL_PENDING, null)
        }
        evidences.forEach { dao.putEvidence(it) }
        val uuid = UUID.randomUUID().toString()
        val payload = BpmOperationRemoteIn(uuid, "COMPLETE_TASK", actor.trim().uppercase(), deviceName,
            Instant.now().toString(), task.revision, task.processId, case.definitionSha256,
            case.remoteId, task.remoteId, case.clientUuid, task.clientUuid, successorUuid,
            result = result.trim(), observations = observations.takeIf { it.isNotBlank() },
            evidences = evidences.map { it.toRemote() })
        dao.updateLocalTask(task.clientUuid, "COMPLETADA", task.revision + 1, result.trim(),
            observations.takeIf { it.isNotBlank() }, true, BpmSyncState.LOCAL_PENDING, null)
        if (next != null && successorUuid != null) {
            dao.putTasks(listOf(BpmTaskLocalEntity(successorUuid, null, case.clientUuid,
                task.processId, next.first, next.second, actor.trim().uppercase(), "PENDIENTE", 1,
                null, null, null, true, BpmSyncState.LOCAL_PENDING, null)))
        }
        dao.putOperation(operationEntity(payload, case.clientUuid, task.clientUuid, successorUuid, latestDependency(case.clientUuid)))
        uuid
    }

    suspend fun queueCaseAdmin(
        actor: String,
        case: BpmCaseLocalEntity,
        operationType: String,
        reason: String
    ): String = database.withTransaction {
        require(case.remoteId != null) { "El caso debe estar confirmado antes de administrarlo" }
        require(operationType in setOf("SUSPEND_CASE", "RESUME_CASE", "CANCEL_CASE")) {
            "Operación administrativa no soportada"
        }
        require(reason.isNotBlank()) { "El motivo es obligatorio" }
        val targetState = when (operationType) {
            "SUSPEND_CASE" -> "SUSPENDIDO"
            "RESUME_CASE" -> "ABIERTO"
            else -> "CANCELADO"
        }
        val operationUuid = UUID.randomUUID().toString()
        val dependency = latestDependency(case.clientUuid)
        val payload = BpmOperationRemoteIn(
            operation_uuid = operationUuid,
            operation_type = operationType,
            actor_code = actor.trim().uppercase(),
            device = deviceName,
            captured_at = Instant.now().toString(),
            base_revision = case.revision,
            process_id = case.processId,
            definition_sha256 = case.definitionSha256,
            case_id = case.remoteId,
            case_client_uuid = case.clientUuid,
            reason = reason.trim()
        )
        dao.updateLocalCase(case.clientUuid, targetState, case.revision + 1,
            BpmSyncState.LOCAL_PENDING, null)
        if (operationType == "CANCEL_CASE") dao.cancelLocalTasks(case.clientUuid, BpmSyncState.LOCAL_PENDING)
        dao.putOperation(operationEntity(payload, case.clientUuid, null, null, dependency))
        operationUuid
    }

    suspend fun queueReassign(
        actor: String,
        task: BpmTaskLocalEntity,
        responsibleId: Int,
        reason: String
    ): String = database.withTransaction {
        require(task.remoteId != null) { "La tarea debe estar confirmada antes de reasignarla" }
        require(responsibleId > 0 && reason.isNotBlank()) { "Responsable y motivo son obligatorios" }
        val case = requireNotNull(dao.caseByUuid(task.caseClientUuid)) { "No existe el caso local" }
        require(case.remoteId != null) { "El caso debe estar confirmado" }
        val uuid = UUID.randomUUID().toString()
        val dependency = latestDependency(case.clientUuid)
        val payload = BpmOperationRemoteIn(
            operation_uuid = uuid, operation_type = "REASSIGN_TASK",
            actor_code = actor.trim().uppercase(), device = deviceName,
            captured_at = Instant.now().toString(), base_revision = case.revision,
            process_id = task.processId, definition_sha256 = case.definitionSha256,
            case_id = case.remoteId, task_id = task.remoteId,
            case_client_uuid = case.clientUuid, task_client_uuid = task.clientUuid,
            responsible_id = responsibleId, reason = reason.trim()
        )
        dao.reassignLocalTask(task.clientUuid, "ID $responsibleId", BpmSyncState.LOCAL_PENDING)
        dao.updateLocalCase(case.clientUuid, case.state, case.revision + 1,
            BpmSyncState.LOCAL_PENDING, null)
        dao.putOperation(operationEntity(payload, case.clientUuid, task.clientUuid, null, dependency))
        uuid
    }

    suspend fun syncPending(): BpmSyncSummary {
        // Repairs evidence state after a process/app restart without replaying an
        // operation the server has already confirmed.
        dao.reconcileConfirmedEvidences()
        val pending = dao.pendingOperations()
        var confirmed = 0; var conflicts = 0; var rejected = 0
        pending.forEach { local ->
            val dependency = local.dependencyUuid?.let { dao.operation(it) }
            when (dependencyDisposition(dependency?.syncState)) {
                DependencyDisposition.BLOCK -> {
                    dao.updateOperation(local.operationUuid, BpmSyncState.CONFLICT,
                        local.serverResultJson,
                        "Dependencia ${dependency!!.operationUuid} terminada en ${dependency.syncState}")
                    conflicts++
                    return@forEach
                }
                DependencyDisposition.WAIT -> return@forEach
                DependencyDisposition.SEND -> Unit
            }
            dao.updateOperation(local.operationUuid, BpmSyncState.SENDING, local.serverResultJson, null)
            try {
                val payload = gson.fromJson(local.payloadJson, BpmOperationRemoteIn::class.java)
                val remote = api.sync(BpmSyncRemoteIn(listOf(payload))).results.single()
                val state = when (remote.status) {
                    "CONFIRMED" -> BpmSyncState.CONFIRMED
                    "CONFLICT" -> BpmSyncState.CONFLICT
                    else -> BpmSyncState.REJECTED
                }
                dao.updateOperation(local.operationUuid, state, gson.toJson(remote), remote.message)
                if (state == BpmSyncState.CONFIRMED) {
                    confirmed++
                    local.caseClientUuid?.let { caseUuid ->
                        val current = dao.caseByUuid(caseUuid)
                        if (current != null) dao.confirmCase(caseUuid, remote.case_id ?: current.remoteId,
                            remote.case_state ?: current.state, remote.case_revision ?: current.revision,
                            remote.server_time, BpmSyncState.CONFIRMED, null)
                    }
                    local.taskClientUuid?.let { taskUuid ->
                        val current = dao.taskByUuid(taskUuid)
                        if (current != null) dao.confirmTask(taskUuid, remote.task_id ?: current.remoteId,
                            remote.task_state ?: current.state, remote.task_revision ?: current.revision,
                            false, BpmSyncState.CONFIRMED, null)
                        if (local.operationType == "COMPLETE_TASK") {
                            dao.updateEvidenceSyncState(taskUuid, BpmSyncState.CONFIRMED, null)
                        }
                    }
                    local.successorClientUuid?.let { successor ->
                        val current = dao.taskByUuid(successor)
                        if (current != null) dao.confirmTask(successor, remote.next_task_id,
                            current.state, current.revision, false, BpmSyncState.CONFIRMED, null)
                    }
                } else if (state == BpmSyncState.CONFLICT) conflicts++ else rejected++
            } catch (error: Exception) {
                dao.updateOperation(local.operationUuid, BpmSyncState.LOCAL_PENDING,
                    local.serverResultJson, error.message?.take(500))
                return BpmSyncSummary(pending.size, confirmed, conflicts, rejected, retryable = 1)
            }
        }
        return BpmSyncSummary(pending.size, confirmed, conflicts, rejected)
    }

    private fun operationEntity(payload: BpmOperationRemoteIn, caseUuid: String?, taskUuid: String?, successorUuid: String?, dependency: String?) =
        BpmOperationLocalEntity(payload.operation_uuid, System.currentTimeMillis(), dependency,
            payload.operation_type, payload.actor_code, payload.device, caseUuid, taskUuid,
            successorUuid, payload.base_revision, payload.definition_sha256, payload.captured_at,
            gson.toJson(payload.copy(dependency_uuid = dependency)), BpmSyncState.LOCAL_PENDING, null, null, 0)

    private suspend fun latestDependency(caseUuid: String): String? =
        dao.pendingOperations().lastOrNull { it.caseClientUuid == caseUuid }?.operationUuid

    private fun initialStage(json: String): Pair<Int, String> {
        val stages = JsonParser.parseString(json).asJsonObject["stages"].asJsonArray
        val stage = stages.firstOrNull { it.asJsonObject["es_inicial"].asInt == 1 }?.asJsonObject
            ?: error("La definición local no tiene etapa inicial")
        return stage["id_etapa"].asInt to stage["nombre"].asString
    }

    private fun nextStage(json: String, current: Int, result: String): Pair<Int, String>? {
        val root = JsonParser.parseString(json).asJsonObject
        val stages = root["stages"].asJsonArray
        val currentStage = stages.first { it.asJsonObject["id_etapa"].asInt == current }.asJsonObject
        if (currentStage["es_final"].asInt == 1) return null
        val matches = root["transitions"].asJsonArray.filter {
            val item = it.asJsonObject
            item["id_etapa_origen"].asInt == current && item["activo"].asInt == 1 &&
                ((!item["resultado"].isJsonNull && item["resultado"].asString == result) ||
                    (item["resultado"].isJsonNull && item["codigo_regla"].asString.equals("SIEMPRE", true)))
        }
        require(matches.size == 1) { "La definición local no determina una ruta única" }
        val destination = matches.single().asJsonObject["id_etapa_destino"].asInt
        val stage = stages.first { it.asJsonObject["id_etapa"].asInt == destination }.asJsonObject
        return destination to stage["nombre"].asString
    }

    private fun BpmEvidenceLocalEntity.toRemote() = BpmEvidenceRemoteIn(
        clientUuid, filename, mimeType, sha256, contentBase64, capturedAt)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
