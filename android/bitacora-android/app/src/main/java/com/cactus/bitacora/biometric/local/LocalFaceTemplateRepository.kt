package com.cactus.bitacora.biometric.local

import android.content.Context
import android.provider.Settings
import android.util.Base64
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
import kotlin.math.sqrt

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
    val errors: Int
)

class LocalFaceTemplateRepository(
    context: Context,
    private val api: BitacoraApi = Api.create()
) {
    companion object {
        const val REQUIRED_ENROLLMENT_CAPTURES = 3
        const val MATCH_THRESHOLD = 0.65f
    }

    private val dao = BitacoraDatabase.getInstance(context).faceTemplateDao()
    private val crypto = FaceTemplateCrypto()
    private val authorization = AppConfig.FACE_TEMPLATE_AUTHORIZATION
    private val deviceId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )

    suspend fun enroll(
        participantId: Int,
        participantCode: String,
        displayName: String,
        embeddings: List<FloatArray>,
        modelVersion: String,
        replaceExisting: Boolean = false
    ) = withContext(Dispatchers.IO) {
        require(embeddings.size >= REQUIRED_ENROLLMENT_CAPTURES)
        check(replaceExisting || dao.getByParticipantId(participantId) == null) {
            "Ya existe un rostro registrado para este participante"
        }
        val template = averageNormalized(embeddings)
        val rawEmbedding = template.toByteArray()
        val digest = rawEmbedding.sha256()
        dao.upsert(
            FaceTemplateEntity(
                participantId = participantId,
                participantCode = participantCode,
                displayName = displayName,
                encryptedEmbedding = crypto.encrypt(template),
                enrolledAtMillis = System.currentTimeMillis(),
                modelVersion = modelVersion,
                active = true,
                embeddingSha256 = digest,
                centralSyncState = "PENDING"
            )
        )
        uploadPending(dao.getByParticipantId(participantId)!!)
    }

    suspend fun identify(embedding: FloatArray): EnrolledParticipant? =
        withContext(Dispatchers.IO) {
            dao.getActive()
                .asSequence()
                .filter { it.modelVersion == "FaceNet-160/128" }
                .map { entity ->
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
                }
                .maxByOrNull { it.similarity }
                ?.takeIf { it.similarity >= MATCH_THRESHOLD }
        }

    suspend fun activeCount(): Int = withContext(Dispatchers.IO) { dao.countActive() }

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

    suspend fun deleteEnrollment(participantId: Int) = withContext(Dispatchers.IO) {
        val local = dao.getByParticipantId(participantId)
        if (local?.remoteTemplateId != null && authorization.isNotBlank()) {
            api.deactivateFaceTemplate(
                authorization,
                local.remoteTemplateId,
                FaceTemplateDeactivateIn(
                    revoked_by = deviceId,
                    revocation_reason = "Eliminado desde dispositivo"
                )
            )
        }
        dao.deleteByParticipantId(participantId)
    }

    suspend fun syncWithCentral(): FaceCentralSyncResult = withContext(Dispatchers.IO) {
        if (authorization.isBlank()) return@withContext FaceCentralSyncResult(0, 0, 1)
        var uploaded = 0
        var errors = 0
        dao.getPendingCentralSync().forEach {
            if (uploadPending(it)) uploaded++ else errors++
        }
        val remote = try {
            api.getAuthorizedFaceTemplates(authorization)
        } catch (_: Exception) {
            return@withContext FaceCentralSyncResult(uploaded, 0, errors + 1)
        }
        val pendingParticipantIds = dao.getPendingCentralSync()
            .mapTo(mutableSetOf()) { it.participantId }
        dao.deactivateCentralCopies()
        remote.filterNot { it.id_participante in pendingParticipantIds }.forEach { template ->
            val raw = Base64.decode(template.embedding_base64, Base64.DEFAULT)
            require(raw.sha256() == template.embedding_sha256.lowercase()) {
                "Hash inválido para participante ${template.id_participante}"
            }
            dao.upsert(
                FaceTemplateEntity(
                    participantId = template.id_participante,
                    participantCode = template.participant_code,
                    displayName = template.display_name,
                    encryptedEmbedding = crypto.encrypt(raw.toFloatArray()),
                    enrolledAtMillis = System.currentTimeMillis(),
                    modelVersion = template.model_version,
                    active = template.active,
                    remoteTemplateId = template.id_face_template,
                    embeddingSha256 = template.embedding_sha256,
                    encryptionVersion = "local-keystore-aesgcm-v1",
                    centralSyncState = "SYNCED"
                )
            )
        }
        FaceCentralSyncResult(
            uploaded,
            remote.count { it.id_participante !in pendingParticipantIds },
            errors
        )
    }

    private suspend fun uploadPending(entity: FaceTemplateEntity): Boolean {
        if (authorization.isBlank()) {
            dao.markCentralError(entity.participantId, "Token de sincronización facial no configurado")
            return false
        }
        return try {
            val raw = crypto.decrypt(entity.encryptedEmbedding).toByteArray()
            val digest = raw.sha256()
            val response = api.enrollFaceTemplate(
                authorization,
                FaceTemplateEnrollIn(
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
            true
        } catch (error: Exception) {
            dao.markCentralError(
                entity.participantId,
                error.message ?: "No fue posible sincronizar la plantilla"
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
}
