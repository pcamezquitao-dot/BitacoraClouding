package com.cactus.bitacora.data

import com.cactus.bitacora.data.local.AreaAdministrativaLocalEntity
import com.cactus.bitacora.data.local.CatalogSyncStateEntity
import com.cactus.bitacora.data.local.EmpleadoAreaLocalEntity
import com.cactus.bitacora.data.local.ParticipanteLocalEntity
import com.cactus.bitacora.data.local.ReferenceCatalogDao
import com.cactus.bitacora.model.AreaOut
import com.cactus.bitacora.model.CatalogAreaOut
import com.cactus.bitacora.model.CatalogAssignmentOut
import com.cactus.bitacora.model.CatalogParticipantOut
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.OfflineCatalogOut
import com.cactus.bitacora.model.ParticipanteOut
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceCatalogRepositoryTest {
    @Test
    fun employeeAndSupervisorValidateLocallyWithoutNetwork() = runBlocking {
        val dao = FakeCatalogDao().apply {
            participants[2] = participant(2, "P0002")
            participants[3] = participant(3, "P0003")
            assignments[2 to 7] = assignment(2, 7, 1)
            assignments[3 to 7] = assignment(3, 7, 3)
        }
        val api = FakeCatalogApi()
        val repository = repository(dao, api)

        assertEquals(2, repository.participantByCode(" p0002 ").id_participante)
        assertEquals(1, repository.assignmentForRole(2, CatalogRole.EMPLOYEE).cargo)
        assertEquals(3, repository.assignmentForRole(3, CatalogRole.SUPERVISOR).cargo)
        assertEquals(0, api.calls)
    }

    @Test
    fun areaAndActiveRelationshipValidateLocally() = runBlocking {
        val dao = FakeCatalogDao().apply {
            areas[7] = area(7)
            assignments[2 to 7] = assignment(2, 7, 1)
        }
        val api = FakeCatalogApi()
        val repository = repository(dao, api)

        assertEquals(7, repository.areaByQr("AREA_ADMINISTRATIVA|7|Falso").id_area)
        repository.requireActiveAssignment(2, 7)
        assertEquals(0, api.calls)
    }

    @Test
    fun invalidRelationshipHasSpecificMessage() {
        val error = assertThrows(CatalogValidationException::class.java) {
            runBlocking {
                repository(FakeCatalogDao().apply { areas[7] = area(7) }, FakeCatalogApi())
                    .requireActiveAssignment(2, 7)
            }
        }
        assertTrue(error.message.orEmpty().contains("no está asignado"))
    }

    @Test
    fun inactiveParticipantAndAreaAreRejected() {
        val dao = FakeCatalogDao().apply {
            participants[2] = participant(2, "P0002").copy(activo = false)
            areas[7] = area(7).copy(activo = false)
        }
        val repository = repository(dao, FakeCatalogApi())
        assertThrows(CatalogValidationException::class.java) {
            runBlocking { repository.participantByCode("P0002") }
        }
        assertThrows(CatalogValidationException::class.java) {
            runBlocking { repository.areaByQr("AREA_ADMINISTRATIVA|7|Administración") }
        }
    }

    @Test
    fun missingOfflineCodeDoesNotExposeNetworkException() {
        val error = assertThrows(CatalogValidationException::class.java) {
            runBlocking {
                repository(FakeCatalogDao(), FakeCatalogApi(failNetwork = true))
                    .participantByCode("P9999")
            }
        }
        assertEquals(ReferenceCatalogRepository.MISSING_LOCAL_MESSAGE, error.message)
    }

    @Test
    fun onlineFallbackStoresParticipantForNextOfflineValidation() = runBlocking {
        val dao = FakeCatalogDao()
        val online = FakeCatalogApi(participant = ParticipanteOut(2, "Ana", "Prueba", "P0002"))
        val first = repository(dao, online)
        assertEquals(2, first.participantByCode("P0002").id_participante)
        val callsAfterDownload = online.calls

        val restartedOffline = repository(dao, FakeCatalogApi(failNetwork = true))
        assertEquals(2, restartedOffline.participantByCode("p0002").id_participante)
        assertEquals(1, callsAfterDownload)
    }

    @Test
    fun failedRefreshPreservesPreviousSnapshot() = runBlocking {
        val dao = FakeCatalogDao().apply {
            participants[2] = participant(2, "P0002")
            areas[7] = area(7)
            assignments[2 to 7] = assignment(2, 7, 1)
        }
        val result = repository(dao, FakeCatalogApi(failNetwork = true)).syncCatalogs()
        assertEquals(false, result.success)
        assertEquals(1, dao.participantCount())
        assertEquals(1, dao.areaCount())
        assertEquals(1, dao.assignmentCount())
    }

    @Test
    fun repeatedSnapshotDoesNotCreateDuplicates() = runBlocking {
        val dao = FakeCatalogDao()
        val api = FakeCatalogApi(snapshot = snapshot())
        val repository = repository(dao, api)
        repository.syncCatalogs()
        repository.syncCatalogs()
        assertEquals(1, dao.participantCount())
        assertEquals(1, dao.areaCount())
        assertEquals(1, dao.assignmentCount())
    }

    @Test
    fun connectivityRecoveryUpdatesCatalogAfterFailure() = runBlocking {
        val dao = FakeCatalogDao()
        val api = FakeCatalogApi(failNetwork = true, snapshot = snapshot())
        val repository = repository(dao, api)
        assertEquals(false, repository.syncCatalogs().success)
        api.failNetwork = false
        assertEquals(true, repository.syncCatalogs().success)
        assertEquals(1, dao.participantCount())
    }

    private fun repository(dao: FakeCatalogDao, api: FakeCatalogApi) =
        ReferenceCatalogRepository(null, api.proxy(), dao)
}

private class FakeCatalogApi(
    var failNetwork: Boolean = false,
    private val participant: ParticipanteOut? = null,
    private val snapshot: OfflineCatalogOut? = null
) {
    var calls = 0

    fun proxy(): BitacoraApi = Proxy.newProxyInstance(
        BitacoraApi::class.java.classLoader,
        arrayOf(BitacoraApi::class.java)
    ) { _, method, _ ->
        calls++
        if (failNetwork) throw IOException("sin red")
        when (method.name) {
            "getParticipanteByQr" -> participant
                ?: throw IOException("participante no configurado")
            "getOfflineCatalogs" -> snapshot ?: snapshot()
            "getAsignacionesActivas" -> emptyList<EmpleadoAreaActivaOut>()
            "getAreaByQr" -> AreaOut(7, "Administración")
            else -> throw UnsupportedOperationException(method.name)
        }
    } as BitacoraApi
}

private class FakeCatalogDao : ReferenceCatalogDao {
    val participants = linkedMapOf<Int, ParticipanteLocalEntity>()
    val areas = linkedMapOf<Int, AreaAdministrativaLocalEntity>()
    val assignments = linkedMapOf<Pair<Int, Int>, EmpleadoAreaLocalEntity>()
    var state: CatalogSyncStateEntity? = null

    override suspend fun participantByCode(code: String) =
        participants.values.firstOrNull { it.codigoQr == code }
    override suspend fun participantById(id: Int) = participants[id]
    override suspend fun areaById(id: Int) = areas[id]
    override suspend fun areaByCode(code: String) = areas.values.firstOrNull { it.codigoQr == code }
    override suspend fun activeAssignmentsForParticipant(participantId: Int) =
        assignments.values.filter { it.idParticipante == participantId && it.activo }
    override suspend fun activeAssignmentsForArea(areaId: Int) =
        assignments.values.filter { it.idArea == areaId && it.activo }
    override suspend fun activeAssignment(participantId: Int, areaId: Int) =
        assignments[participantId to areaId]?.takeIf { it.activo }
    override suspend fun upsertParticipant(item: ParticipanteLocalEntity) {
        participants[item.idParticipante] = item
    }
    override suspend fun upsertArea(item: AreaAdministrativaLocalEntity) {
        areas[item.idArea] = item
    }
    override suspend fun upsertAssignment(item: EmpleadoAreaLocalEntity) {
        assignments[item.idParticipante to item.idArea] = item
    }
    override suspend fun upsertParticipants(items: List<ParticipanteLocalEntity>) =
        items.forEach { upsertParticipant(it) }
    override suspend fun upsertAreas(items: List<AreaAdministrativaLocalEntity>) =
        items.forEach { upsertArea(it) }
    override suspend fun upsertAssignments(items: List<EmpleadoAreaLocalEntity>) =
        items.forEach { upsertAssignment(it) }
    override suspend fun upsertSyncState(state: CatalogSyncStateEntity) {
        this.state = state
    }
    override suspend fun markAllParticipantsInactive() {
        participants.replaceAll { _, value -> value.copy(activo = false) }
    }
    override suspend fun markAllAreasInactive() {
        areas.replaceAll { _, value -> value.copy(activo = false) }
    }
    override suspend fun markAllAssignmentsInactive() {
        assignments.replaceAll { _, value -> value.copy(activo = false) }
    }
    override suspend fun participantCount() = participants.values.count { it.activo }
    override suspend fun areaCount() = areas.values.count { it.activo }
    override suspend fun assignmentCount() = assignments.values.count { it.activo }
    override suspend fun syncState() = state
}

private fun participant(id: Int, code: String) =
    ParticipanteLocalEntity(id, code, "Nombre", "Apellido", null, true, null, 1)

private fun area(id: Int) =
    AreaAdministrativaLocalEntity(
        id,
        "AREA_ADMINISTRATIVA|$id|Administración",
        "Administración",
        true,
        null,
        1
    )

private fun assignment(participantId: Int, areaId: Int, cargo: Int) =
    EmpleadoAreaLocalEntity(participantId, areaId, cargo, null, true, null, 1)

private fun snapshot() = OfflineCatalogOut(
    generated_at = "2026-07-19T00:00:00Z",
    participantes = listOf(CatalogParticipantOut(2, "P0002", "Ana", "Prueba")),
    areas = listOf(CatalogAreaOut(7, "Administración")),
    empleado_areas = listOf(CatalogAssignmentOut(2, 7, 1))
)
