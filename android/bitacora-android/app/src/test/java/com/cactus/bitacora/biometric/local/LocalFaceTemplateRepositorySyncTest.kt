package com.cactus.bitacora.biometric.local

import com.cactus.bitacora.data.BitacoraApi
import com.cactus.bitacora.data.local.FaceTemplateDao
import com.cactus.bitacora.data.local.FaceTemplateEntity
import com.cactus.bitacora.model.FaceTemplateAuthorizedOut
import com.cactus.bitacora.model.FaceTemplateDeactivateIn
import com.cactus.bitacora.model.FaceTemplateEnrollIn
import com.cactus.bitacora.model.FaceTemplateMetadataOut
import java.io.IOException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalFaceTemplateRepositorySyncTest {
    @Test
    fun offlineEnrollmentPersistsAndSynchronizesAfterConnectivityReturns() = runBlocking {
        val dao = FakeFaceTemplateDao()
        val server = FakeFaceServer(online = false)
        val firstRun = repository(dao, server)

        firstRun.enroll(14, "P0014", "Participante 14", samples(0.2f), MODEL)

        val pending = dao.getByParticipantId(14)!!
        assertTrue(pending.active)
        assertEquals("ERROR", pending.centralSyncState)
        assertEquals(1, pending.syncAttempts)

        server.online = true
        val reopenedApp = repository(dao, server)
        val result = reopenedApp.syncWithCentral()

        assertEquals(1, result.uploaded)
        assertEquals("SINCRONIZADO", dao.getByParticipantId(14)?.centralSyncState)
        assertEquals(1, server.enrollCalls)
    }

    @Test
    fun repeatedSynchronizationDoesNotUploadDuplicate() = runBlocking {
        val dao = FakeFaceTemplateDao()
        val server = FakeFaceServer()
        val repository = repository(dao, server)

        repository.enroll(14, "P0014", "Participante 14", samples(0.2f), MODEL)
        repository.syncWithCentral()
        repository.syncWithCentral()

        assertEquals(1, server.enrollCalls)
        assertEquals(1, server.templates.size)
        assertEquals(1, dao.getActive().size)
    }

    @Test
    fun replacingEnrollmentUploadsUpdatedTemplate() = runBlocking {
        val dao = FakeFaceTemplateDao()
        val server = FakeFaceServer()
        val repository = repository(dao, server)

        repository.enroll(14, "P0014", "Participante 14", samples(0.2f), MODEL)
        val first = dao.getByParticipantId(14)!!
        repository.enroll(
            14,
            "P0014",
            "Participante 14",
            samples(0.7f),
            MODEL,
            replaceExisting = true
        )
        val replacement = dao.getByParticipantId(14)!!

        assertEquals(2, server.enrollCalls)
        assertNotEquals(first.embeddingSha256, replacement.embeddingSha256)
        assertNotEquals(first.remoteTemplateId, replacement.remoteTemplateId)
        assertEquals("SINCRONIZADO", replacement.centralSyncState)
    }

    @Test
    fun centralTemplateDownloadsIntoSqliteQueueStore() = runBlocking {
        val dao = FakeFaceTemplateDao()
        val server = FakeFaceServer().apply { addRemote(21, "P0021", 0.4f) }

        val result = repository(dao, server).syncWithCentral()
        val downloaded = dao.getByParticipantId(21)!!

        assertEquals(1, result.downloaded)
        assertTrue(downloaded.active)
        assertEquals("SINCRONIZADO", downloaded.centralSyncState)
        assertEquals(server.templates.single().embedding_sha256, downloaded.embeddingSha256)
    }

    @Test
    fun recognitionDownloadsCentralTemplatesWhenFreshInstallIsEmpty() = runBlocking {
        val dao = FakeFaceTemplateDao()
        val server = FakeFaceServer().apply {
            addRemote(2, "P0002", 0.2f)
            addRemote(30, "P0030", 0.3f)
        }

        val count = repository(dao, server).ensureActiveTemplatesAvailable()

        assertEquals(2, count)
        assertEquals(setOf("P0002", "P0030"), dao.getActive().map { it.participantCode }.toSet())
    }

    @Test
    fun recognitionUsesExistingLocalTemplatesWithoutRequiringNetwork() = runBlocking {
        val dao = FakeFaceTemplateDao()
        dao.upsert(template(30, "P0030", "SINCRONIZADO"))
        val server = FakeFaceServer(online = false)

        assertEquals(1, repository(dao, server).ensureActiveTemplatesAvailable())
    }

    @Test
    fun recognitionReportsDownloadFailureAndKeepsFreshInstallEmpty() = runBlocking {
        val dao = FakeFaceTemplateDao()
        val repository = repository(dao, FakeFaceServer(online = false))

        val error = runCatching { repository.ensureActiveTemplatesAvailable() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0, dao.countActive())
    }

    @Test
    fun deactivatedTemplateStopsMatchingAndSynchronizesDeletionLater() = runBlocking {
        val dao = FakeFaceTemplateDao()
        val server = FakeFaceServer().apply { addRemote(14, "P0014", 0.3f) }
        val repository = repository(dao, server)
        repository.syncWithCentral()

        server.online = false
        repository.deleteEnrollment(14)
        val pendingDelete = dao.getByParticipantId(14)!!
        assertFalse(pendingDelete.active)
        assertEquals("ERROR", pendingDelete.centralSyncState)
        assertNull(repository.identify(floatArrayOf(0.3f, 0.3f, 0.3f, 0.3f)))

        server.online = true
        repository.syncWithCentral()
        assertNull(dao.getByParticipantId(14))
        assertEquals(1, server.deactivateCalls)
    }

    @Test
    fun emptyRemoteCatalogDoesNotDeactivateLocalSynchronizedTemplate() = runBlocking {
        val dao = FakeFaceTemplateDao()
        dao.upsert(template(2, "P0002", "SINCRONIZADO"))

        repository(dao, FakeFaceServer()).syncWithCentral()

        assertTrue(dao.getByParticipantId(2)!!.active)
        assertEquals(1, dao.countActive())
    }

    @Test
    fun pendingSyncedAndRecoverableErrorRemainAvailableForRecognition() = runBlocking {
        listOf("PENDIENTE_CREAR", "PENDIENTE_ACTUALIZAR", "SINCRONIZADO", "ERROR")
            .forEachIndexed { index, state ->
                val dao = FakeFaceTemplateDao()
                dao.upsert(template(index + 1, "P000${index + 1}", state))
                assertEquals(1, repository(dao, FakeFaceServer()).activeCount())
            }
    }

    @Test
    fun logicallyDeletedTemplateIsNotAvailable() = runBlocking {
        val dao = FakeFaceTemplateDao()
        dao.upsert(template(2, "P0002", "PENDIENTE_ELIMINAR", active = false))

        assertEquals(0, repository(dao, FakeFaceServer()).activeCount())
    }

    @Test
    fun participantCodeP0002RemainsTextAndDoesNotRequireRole() = runBlocking {
        val dao = FakeFaceTemplateDao()
        dao.upsert(template(2, "P0002", "PENDIENTE_CREAR"))
        val reopened = repository(dao, FakeFaceServer())

        assertEquals("P0002", dao.getByParticipantId(2)?.participantCode)
        assertEquals(1, reopened.activeCount())
    }

    @Test
    fun synchronizedTemplateAffectedByEmptyCatalogCanBeRecoveredWithoutPendingDelete() =
        runBlocking {
            val affected = template(2, "P0002", "SINCRONIZADO", active = false)
            assertEquals("SINCRONIZADO", affected.centralSyncState)
            assertFalse(affected.active)
            assertNotEquals("PENDIENTE_ELIMINAR", affected.centralSyncState)
        }

    private fun template(
        participantId: Int,
        code: String,
        state: String,
        active: Boolean = true
    ) = FaceTemplateEntity(
        participantId = participantId,
        localSyncUuid = "uuid-$participantId",
        participantCode = code,
        displayName = "Participante $participantId",
        encryptedEmbedding = TestFaceTemplateCipher.encrypt(
            floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f)
        ),
        enrolledAtMillis = 1L,
        modelVersion = MODEL,
        active = active,
        centralSyncState = state
    )

    private fun repository(dao: FaceTemplateDao, server: FakeFaceServer) =
        LocalFaceTemplateRepository(
            context = null,
            api = server.api,
            dao = dao,
            crypto = TestFaceTemplateCipher,
            authorization = "Bearer test-token",
            deviceId = "test-device"
        )

    private fun samples(value: Float) = List(3) {
        floatArrayOf(value, value + 0.1f, value + 0.2f, value + 0.3f)
    }

    private companion object {
        const val MODEL = "FaceNet-160/128"
    }
}

private object TestFaceTemplateCipher : FaceTemplateCipher {
    override fun encrypt(embedding: FloatArray): ByteArray = embedding.toBytes()
    override fun decrypt(payload: ByteArray): FloatArray = payload.toFloats()
}

private class FakeFaceTemplateDao : FaceTemplateDao {
    private val rows = linkedMapOf<Int, FaceTemplateEntity>()

    override suspend fun upsert(template: FaceTemplateEntity) {
        rows[template.participantId] = template
    }

    override suspend fun getByParticipantId(participantId: Int) = rows[participantId]
    override suspend fun getActive() = rows.values.filter { it.active }
    override suspend fun getPendingCentralSync() = rows.values.filter {
        it.centralSyncState in setOf(
            "PENDIENTE_CREAR",
            "PENDIENTE_ACTUALIZAR",
            "PENDIENTE_ELIMINAR",
            "ERROR"
        )
    }
    override suspend fun countActive() = rows.values.count { it.active }
    override suspend fun deactivate(participantId: Int) {
        rows[participantId]?.let { rows[participantId] = it.copy(active = false) }
    }
    override suspend fun markCentralSynced(
        participantId: Int,
        remoteTemplateId: Int,
        embeddingSha256: String
    ) {
        rows[participantId]?.let {
            rows[participantId] = it.copy(
                remoteTemplateId = remoteTemplateId,
                embeddingSha256 = embeddingSha256,
                centralSyncState = "SINCRONIZADO",
                lastSyncError = null
            )
        }
    }
    override suspend fun markCentralError(participantId: Int, message: String) {
        rows[participantId]?.let {
            rows[participantId] = it.copy(
                centralSyncState = "ERROR",
                syncAttempts = it.syncAttempts + 1,
                lastSyncError = message
            )
        }
    }
    override suspend fun deactivateCentralCopies() {
        rows.replaceAll { _, value ->
            if (value.centralSyncState == "SINCRONIZADO") value.copy(active = false) else value
        }
    }
    override suspend fun markPendingDelete(participantId: Int) {
        rows[participantId]?.let {
            rows[participantId] = it.copy(
                active = false,
                centralSyncState = "PENDIENTE_ELIMINAR",
                lastSyncError = null
            )
        }
    }
    override suspend fun updateLocalSyncUuid(participantId: Int, localSyncUuid: String) {
        rows[participantId]?.let { rows[participantId] = it.copy(localSyncUuid = localSyncUuid) }
    }
    override suspend fun countPending() = rows.values.count {
        it.centralSyncState in setOf(
            "PENDIENTE_CREAR",
            "PENDIENTE_ACTUALIZAR",
            "PENDIENTE_ELIMINAR"
        )
    }
    override suspend fun countErrors() = rows.values.count { it.centralSyncState == "ERROR" }
    override suspend fun deleteByParticipantId(participantId: Int) {
        rows.remove(participantId)
    }
}

private class FakeFaceServer(var online: Boolean = true) {
    val templates = mutableListOf<FaceTemplateAuthorizedOut>()
    var enrollCalls = 0
    var deactivateCalls = 0
    private var nextId = 1

    val api: BitacoraApi = Proxy.newProxyInstance(
        BitacoraApi::class.java.classLoader,
        arrayOf(BitacoraApi::class.java)
    ) { _, method, args ->
        if (!online) throw IOException("sin red")
        when (method.name) {
            "enrollFaceTemplate" -> enroll(args!![1] as FaceTemplateEnrollIn)
            "getAuthorizedFaceTemplates" -> templates.filter { it.active }
            "deactivateFaceTemplate" -> {
                deactivate(args!![1] as Int, args[2] as FaceTemplateDeactivateIn)
                Unit
            }
            else -> throw UnsupportedOperationException(method.name)
        }
    } as BitacoraApi

    fun addRemote(participantId: Int, code: String, value: Float) {
        val raw = floatArrayOf(value, value, value, value).toBytes()
        val digest = raw.sha256()
        templates += FaceTemplateAuthorizedOut(
            id_face_template = nextId++,
            id_participante = participantId,
            participant_code = code,
            display_name = "Participante $participantId",
            embedding_sha256 = digest,
            model_version = "FaceNet-160/128",
            encryption_version = "server-aesgcm-v1",
            enrolled_at = "2026-07-20T00:00:00Z",
            active = true,
            embedding_base64 = Base64.getEncoder().encodeToString(raw)
        )
    }

    private fun enroll(payload: FaceTemplateEnrollIn): FaceTemplateMetadataOut {
        enrollCalls++
        templates.replaceAll {
            if (it.id_participante == payload.id_participante) it.copy(active = false) else it
        }
        val id = nextId++
        templates += FaceTemplateAuthorizedOut(
            id_face_template = id,
            id_participante = payload.id_participante,
            participant_code = payload.participant_code,
            display_name = payload.display_name,
            embedding_sha256 = payload.embedding_sha256,
            model_version = payload.model_version,
            encryption_version = payload.encryption_version,
            enrolled_at = "2026-07-20T00:00:00Z",
            active = true,
            device_id = payload.device_id,
            embedding_base64 = payload.embedding_base64
        )
        return FaceTemplateMetadataOut(
            id,
            payload.id_participante,
            payload.participant_code,
            payload.display_name,
            payload.embedding_sha256,
            payload.model_version,
            payload.encryption_version,
            "2026-07-20T00:00:00Z",
            true,
            payload.device_id
        )
    }

    private fun deactivate(id: Int, payload: FaceTemplateDeactivateIn) {
        payload.revocation_reason
        deactivateCalls++
        templates.replaceAll { if (it.id_face_template == id) it.copy(active = false) else it }
    }
}

private fun FloatArray.toBytes(): ByteArray =
    ByteBuffer.allocate(size * Float.SIZE_BYTES).also { buffer -> forEach(buffer::putFloat) }.array()

private fun ByteArray.toFloats(): FloatArray {
    val buffer = ByteBuffer.wrap(this)
    return FloatArray(size / Float.SIZE_BYTES) { buffer.float }
}

private fun ByteArray.sha256(): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { "%02x".format(it) }
