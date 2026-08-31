package com.cactus.bitacora.feature.supervisorevents

import com.cactus.bitacora.data.local.SupervisorEventDao
import com.cactus.bitacora.data.local.SupervisorEventLocalEntity
import com.cactus.bitacora.data.local.SyncStatus
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisorEventRepositoryTest {
    @Test
    fun `offline save is pending and can be queried by participant`() = runBlocking {
        val dao = FakeSupervisorEventDao()
        val repository = SupervisorEventRepository(dao, SuccessfulApi())

        repository.saveOffline(draft())

        val stored = repository.eventsForParticipant("P0002", 15)
        assertEquals(1, stored.size)
        assertEquals(SyncStatus.PENDIENTE_CREAR, stored.single().syncStatus)
        assertEquals(90, java.time.Duration.between(
            java.time.LocalTime.parse(stored.single().horaInicio),
            java.time.LocalTime.parse(stored.single().horaFinal)
        ).toMinutes())
    }

    @Test
    fun `offline item remains pending when endpoint is unavailable`() = runBlocking {
        val dao = FakeSupervisorEventDao()
        val repository = SupervisorEventRepository(dao, UnavailableApi())
        repository.saveOffline(draft())

        val result = repository.syncPending()

        assertEquals(1, result.reviewed)
        assertEquals(0, result.synced)
        assertEquals(1, result.errors)
        assertEquals(1, result.retryableErrors)
        assertEquals(SyncStatus.PENDIENTE_CREAR, dao.values.single().syncStatus)
        assertTrue(dao.values.single().errorMessage.orEmpty().isNotBlank())
    }

    private fun draft() = SupervisorEventDraft(
        supervisorCode = "P0002",
        participantId = 15,
        areaId = 1,
        type = SupervisorEventType.PERMISSION,
        startDate = "2026-08-30",
        startTime = "08:00",
        endTime = "09:30",
        observations = "Permiso médico"
    )
}

private class SuccessfulApi : SupervisorEventApi {
    override suspend fun create(payload: SupervisorEventRemoteIn) =
        SupervisorEventRemoteOut(id_novedad = 101)
}

private class UnavailableApi : SupervisorEventApi {
    override suspend fun create(payload: SupervisorEventRemoteIn): SupervisorEventRemoteOut =
        throw IOException("Sin conexión")
}

private class FakeSupervisorEventDao : SupervisorEventDao {
    val values = mutableListOf<SupervisorEventLocalEntity>()

    override suspend fun insert(value: SupervisorEventLocalEntity): Long {
        val id = (values.size + 1).toLong()
        values += value.copy(localId = id)
        return id
    }

    override suspend fun pending(): List<SupervisorEventLocalEntity> =
        values.filter { it.syncStatus == SyncStatus.PENDIENTE_CREAR || it.syncStatus == SyncStatus.ERROR }

    override suspend fun eventsForParticipant(
        supervisorCode: String,
        participantId: Int
    ): List<SupervisorEventLocalEntity> = values
        .filter { it.supervisorCode == supervisorCode && it.idParticipante == participantId }
        .sortedByDescending { it.createdAtMillis }

    override suspend fun updateSync(
        id: Long,
        backendId: Long?,
        status: SyncStatus,
        error: String?,
        updated: Long
    ) {
        val index = values.indexOfFirst { it.localId == id }
        values[index] = values[index].copy(
            backendId = backendId,
            syncStatus = status,
            syncAttempts = values[index].syncAttempts + 1,
            errorMessage = error,
            updatedAtMillis = updated
        )
    }
}
