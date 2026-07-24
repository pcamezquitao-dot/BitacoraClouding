package com.cactus.bitacora.biometric.local

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.cactus.bitacora.biometric.technical.FaceTechnicalMath
import com.cactus.bitacora.data.Api
import com.cactus.bitacora.data.BitacoraApi
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.FaceTemplateEntity
import com.cactus.bitacora.model.FaceTemplateDeactivateIn
import com.cactus.bitacora.model.FaceTemplateEnrollIn
import com.cactus.bitacora.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.sqrt
import retrofit2.HttpException

data class EnrolledParticipant(
    val participantId: Int,
    val participantCode: String,
    val displayName: String,
    val similarity: Float
)

data class FaceEnrollmentRecord(
    val participantId: Int,
    val participantCode: String,
    val displayName: String,
    val enrolledAtMillis: Long
)

data class FaceCentralSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val errors: Int,
    val retryableErrors: Int = 0,
    val errorMessages: List<String> = emptyList()
)

class LocalFaceTemplateRepository(
    context: Context?,
    private val api: BitacoraApi = Api.create(),
    private val dao: com.cactus.bitacora.data.local.FaceTemplateDao =
        BitacoraDatabase.getInstance(requireNotNull(context)).faceTemplateDao(),
    private val crypto: FaceTemplateCipher = FaceTemplateCrypto(),
    private val authorization: String = AppConfig.FACE_TEMPLATE_AUTHORIZATION,
    private val deviceId: String = context?.let {
        Settings.Secure.getString(it.contentResolver, Settings.Secure.ANDROID_ID)
    } ?: "unknown-device"
) {
    companion object {
        const val REQUIRED_ENROLLMENT_CAPTURES = 3
        const val MATCH_THRESHOLD = 0.65f
        private const val SYNC_TAG = "OfflineSync"
    }

    suspend fun enroll(
        participantId: Int,
        participantCode: String,
        displayName: String,
        embeddings: List<FloatArray>,
        modelVersion: String,
        replaceExisting: Boolean = false
    ) = withContext(Dispatchers.IO) {
        require(embeddings.size >= REQUIRED_ENROLLMENT_CAPTURES)
        val existing = dao.getByParticipantId(participantId)
        check(replaceExisting || existing == null) {
            "Ya existe un rostro registrado para este participante"
        }
        val template = averageNormalized(embeddings)
        val rawEmbedding = template.toByteArray()
        val digest = rawEmbedding.sha256()
        dao.upsert(
            FaceTemplateEntity(
                participantId = participantId,
                localSyncUuid = UUID.randomUUID().toString(),
                participantCode = participantCode,
                displayName = displayName,
                encryptedEmbedding = crypto.encrypt(template),
                enrolledAtMillis = System.currentTimeMillis(),
                modelVersion = modelVersion,
                active = true,
                embeddingSha256 = digest,
                remoteTemplateId = existing?.remoteTemplateId,
                centralSyncState = if (existing?.remoteTemplateId == null) {
                    "PENDIENTE_CREAR"
                } else {
                    "PENDIENTE_ACTUALIZAR"
                }
            )
        )
        val saved = dao.getByParticipantId(participantId)!!
        Log.i(
            SYNC_TAG,
            "face localUuid=${saved.localSyncUuid} participantId=${saved.participantId} " +
                "state=${saved.centralSyncState} serverId=${saved.remoteTemplateId}"
        )
        uploadPending(saved)
    }

    suspend fun identify(embedding: FloatArray): EnrolledParticipant? =
        withContext(Dispatchers.IO) {
            val active = dao.getActive()
            Log.i(SYNC_TAG, "face recognition templates found=${active.size}")
            active
                .asSequence()
                .mapNotNull { entity ->
                    if (entity.modelVersion != "FaceNet-160/128") {
                        Log.w(
                            SYNC_TAG,
                            "face recognition skipped participantId=${entity.participantId} " +
                                "state=${entity.centralSyncState} reason=model_version"
                        )
                        return@mapNotNull null
                    }
                    runCatching {
                        val similarity = FaceTechnicalMath.cosineSimilarity(
                            normalize(embedding),
                            crypto.decrypt(entity.encryptedEmbedding)
                        )
                        EnrolledParticipant(
                            entity.participantId,
                            entity.participantCode,
                            entity.displayName,
                            similarity
                        )
                    }.onFailure {
                        Log.e(
                            SYNC_TAG,
                            "face recognition skipped participantId=${entity.participantId} " +
                                "state=${entity.centralSyncState} reason=decrypt_error"
                        )
                    }.getOrNull()
                }
                .maxByOrNull { it.similarity }
                ?.takeIf { it.similarity >= MATCH_THRESHOLD }
        }

    suspend fun activeCount(): Int = withContext(Dispatchers.IO) {
        val active = dao.getActive()
        if (active.isEmpty()) return@withContext 0
        val usable = active.count { entity ->
            entity.modelVersion == "FaceNet-160/128" &&
                runCatching { crypto.decrypt(entity.encryptedEmbedding) }
                    .onFailure {
                        Log.e(
                            SYNC_TAG,
                            "face load failed participantId=${entity.participantId} " +
                                "state=${entity.centralSyncState} reason=decrypt_error"
                        )
                    }
                    .isSuccess
        }
        Log.i(SYNC_TAG, "face templates found=${active.size} usable=$usable")
        check(usable > 0) { "No fue posible cargar los enrolamientos locales" }
        usable
    }

    suspend fun ensureActiveTemplatesAvailable(): Int {
        val localCount = activeCount()
        if (localCount > 0) return localCount

        Log.i(SYNC_TAG, "face local catalog empty; downloading central templates")
        val result = syncWithCentral()
        val downloadedCount = activeCount()
        if (downloadedCount == 0 && result.errors > 0) {
            throw IllegalStateException(
                result.errorMessages.firstOrNull()
                    ?: "No fue posible descargar los enrolamientos centrales"
            )
        }
        return downloadedCount
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) { dao.countPending() }

    suspend fun errorCount(): Int = withContext(Dispatchers.IO) { dao.countErrors() }

    suspend fun getEnrollment(participantId: Int): FaceEnrollmentRecord? =
        withContext(Dispatchers.IO) {
            dao.getByParticipantId(participantId)
                ?.takeIf { it.active }
                ?.let {
                    FaceEnrollmentRecord(
                        participantId = it.participantId,
                        participantCode = it.participantCode,
                        displayName = it.displayName,
                        enrolledAtMillis = it.enrolledAtMillis
                    )
                }
        }

    suspend fun getSyncState(participantId: Int): String? =
        withContext(Dispatchers.IO) {
            dao.getByParticipantId(participantId)?.centralSyncState
        }

    suspend fun deleteEnrollment(participantId: Int) = withContext(Dispatchers.IO) {
        val local = dao.getByParticipantId(participantId)
        if (local == null) return@withContext
        if (local.remoteTemplateId == null) {
            dao.deleteByParticipantId(participantId)
        } else {
            dao.markPendingDelete(participantId)
            uploadPending(dao.getByParticipantId(participantId)!!)
        }
    }

    suspend fun syncWithCentral(): FaceCentralSyncResult = withContext(Dispatchers.IO) {
        if (authorization.isBlank()) {
            val message = "Token de sincronización facial no configurado"
            return@withContext FaceCentralSyncResult(
                uploaded = 0,
                downloaded = 0,
                errors = 1,
                retryableErrors = 0,
                errorMessages = listOf(message)
            )
        }
        var uploaded = 0
        var errors = 0
        var retryableErrors = 0
        val errorMessages = mutableListOf<String>()
        dao.getPendingCentralSync().forEach {
            if (uploadPending(it)) {
                uploaded++
            } else {
                errors++
                val lastError = dao.getByParticipantId(it.participantId)
                    ?.lastSyncError
                    ?: "No fue posible sincronizar la plantilla de ${it.participantCode}"
                errorMessages += lastError
                if (lastError.isRetryableDiagnostic()) retryableErrors++
            }
        }
        val remote = try {
            api.getAuthorizedFaceTemplates(authorization)
        } catch (error: Exception) {
            val diagnostic = error.safeSyncDiagnostic()
            return@withContext FaceCentralSyncResult(
                uploaded = uploaded,
                downloaded = 0,
                errors = errors + 1,
                retryableErrors = retryableErrors +
                    if (error.isRetryableCentralError()) 1 else 0,
                errorMessages = errorMessages + diagnostic
            )
        }
        val pendingParticipantIds = dao.getPendingCentralSync()
            .mapTo(mutableSetOf()) { it.participantId }
        val centralCopies = remote.filterNot {
            it.id_participante in pendingParticipantIds
        }.map { template ->
            val raw = Base64.decode(template.embedding_base64, Base64.DEFAULT)
            require(raw.sha256() == template.embedding_sha256.lowercase()) {
                "Hash inválido para participante ${template.id_participante}"
            }
            FaceTemplateEntity(
                participantId = template.id_participante,
                localSyncUuid = UUID.randomUUID().toString(),
                participantCode = template.participant_code,
                displayName = template.display_name,
                encryptedEmbedding = crypto.encrypt(raw.toFloatArray()),
                enrolledAtMillis = System.currentTimeMillis(),
                modelVersion = template.model_version,
                active = template.active,
                remoteTemplateId = template.id_face_template,
                embeddingSha256 = template.embedding_sha256,
                encryptionVersion = "local-keystore-aesgcm-v1",
                centralSyncState = "SINCRONIZADO"
            )
        }
        if (centralCopies.isNotEmpty()) {
            dao.replaceCentralCopiesAtomically(centralCopies)
        } else {
            Log.i(SYNC_TAG, "face remote catalog empty; local templates preserved")
        }
        FaceCentralSyncResult(
            uploaded,
            remote.count { it.id_participante !in pendingParticipantIds },
            errors,
            retryableErrors,
            errorMessages
        )
    }

    private suspend fun uploadPending(entity: FaceTemplateEntity): Boolean {
        val localUuid = entity.localSyncUuid.ifBlank {
            UUID.nameUUIDFromBytes(
                "${entity.participantId}:${entity.enrolledAtMillis}".toByteArray()
            ).toString().also { dao.updateLocalSyncUuid(entity.participantId, it) }
        }
        val endpoint = "POST ${AppConfig.BASE_URL}face-templates/enroll"
        Log.i(SYNC_TAG, "FACE_SYNC_START participantCode=${entity.participantCode}")
        Log.i(SYNC_TAG, "FACE_SYNC_URL=$endpoint")
        if (authorization.isBlank()) {
            val message = "Token de sincronización facial no configurado"
            Log.e(SYNC_TAG, "FACE_SYNC_HTTP=NO_ENVIADO")
            Log.e(SYNC_TAG, "FACE_SYNC_ERROR=$message")
            Log.e(
                SYNC_TAG,
                "face localUuid=$localUuid participantId=${entity.participantId} " +
                    "state=${entity.centralSyncState} endpoint=$endpoint " +
                    "http=NO_ENVIADO error=$message"
            )
            dao.markCentralError(entity.participantId, message)
            return false
        }
        return try {
            if (
                entity.centralSyncState == "PENDIENTE_ELIMINAR" ||
                (entity.centralSyncState == "ERROR" && !entity.active)
            ) {
                val remoteId = requireNotNull(entity.remoteTemplateId) {
                    "La plantilla pendiente de eliminar no tiene ID del servidor"
                }
                api.deactivateFaceTemplate(
                    authorization,
                    remoteId,
                    FaceTemplateDeactivateIn(
                        revoked_by = deviceId,
                        revocation_reason = "Eliminado desde dispositivo"
                    )
                )
                dao.deleteByParticipantId(entity.participantId)
                return true
            }
            val raw = crypto.decrypt(entity.encryptedEmbedding).toByteArray()
            val digest = raw.sha256()
            val response = api.enrollFaceTemplate(
                authorization,
                FaceTemplateEnrollIn(
                    client_uuid = localUuid,
                    id_participante = entity.participantId,
                    participant_code = entity.participantCode,
                    display_name = entity.displayName,
                    embedding_base64 = Base64.encodeToString(raw, Base64.NO_WRAP),
                    embedding_sha256 = digest,
                    model_version = entity.modelVersion,
                    device_id = deviceId
                )
            )
            dao.markCentralSynced(entity.participantId, response.id_face_template, digest)
            Log.i(SYNC_TAG, "FACE_SYNC_HTTP=200")
            Log.i(SYNC_TAG, "FACE_SYNC_SUCCESS participantCode=${entity.participantCode}")
            Log.i(
                SYNC_TAG,
                "face localUuid=$localUuid participantId=${entity.participantId} " +
                    "state=SINCRONIZADO endpoint=$endpoint http=200 " +
                    "serverId=${response.id_face_template}"
            )
            true
        } catch (error: Exception) {
            val diagnostic = error.safeSyncDiagnostic()
            Log.e(
                SYNC_TAG,
                "FACE_SYNC_HTTP=${(error as? HttpException)?.code() ?: "SIN_RESPUESTA"}"
            )
            Log.e(SYNC_TAG, "FACE_SYNC_ERROR=$diagnostic")
            Log.e(
                SYNC_TAG,
                "face localUuid=$localUuid participantId=${entity.participantId} " +
                    "state=ERROR endpoint=$endpoint $diagnostic"
            )
            dao.markCentralError(
                entity.participantId,
                diagnostic
            )
            false
        }
    }

    internal fun averageNormalized(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty())
        val size = embeddings.first().size
        require(embeddings.all { it.size == size })
        val average = FloatArray(size)
        embeddings.forEach { embedding ->
            normalize(embedding).forEachIndexed { index, value -> average[index] += value }
        }
        for (index in average.indices) average[index] /= embeddings.size
        return normalize(average)
    }

    private fun normalize(source: FloatArray): FloatArray {
        val norm = sqrt(source.sumOf { (it * it).toDouble() }).toFloat()
        if (norm == 0f) return source.copyOf()
        return FloatArray(source.size) { source[it] / norm }
    }

    private fun FloatArray.toByteArray(): ByteArray =
        ByteBuffer.allocate(size * Float.SIZE_BYTES).also { buffer ->
            forEach(buffer::putFloat)
        }.array()

    private fun ByteArray.toFloatArray(): FloatArray {
        require(size % Float.SIZE_BYTES == 0)
        val buffer = ByteBuffer.wrap(this)
        return FloatArray(size / Float.SIZE_BYTES) { buffer.float }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this)
            .joinToString("") { "%02x".format(it) }

    private fun Throwable.safeSyncDiagnostic(): String =
        if (this is HttpException) {
            val serverDetail = response()?.errorBody()?.string()
                ?.replace(Regex("\\s+"), " ")
                ?.take(300)
            "http=${code()} message=${serverDetail?.takeIf { it.isNotBlank() } ?: message().take(200)}"
        } else {
            "http=NO_DISPONIBLE message=${message?.take(200) ?: javaClass.simpleName}"
        }

    private fun Throwable.isRetryableCentralError(): Boolean =
        this !is HttpException || code() == 408 || code() == 429 || code() >= 500

    private fun String.isRetryableDiagnostic(): Boolean =
        startsWith("http=NO_DISPONIBLE") ||
            startsWith("http=408") ||
            startsWith("http=429") ||
            Regex("^http=5\\d\\d").containsMatchIn(this)
}
