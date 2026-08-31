package com.cactus.bitacora.data

import com.cactus.bitacora.data.local.AreaAdministrativaLocalEntity
import com.cactus.bitacora.data.local.CatalogPreparationStateEntity
import com.cactus.bitacora.data.local.CatalogSyncStateEntity
import com.cactus.bitacora.data.local.EmpleadoAreaLocalEntity
import com.cactus.bitacora.data.local.ParticipanteLocalEntity
import com.cactus.bitacora.data.local.ReferenceCatalogDao
import com.cactus.bitacora.data.local.WorkScheduleDetailLocalEntity
import com.cactus.bitacora.data.local.WorkScheduleLocalEntity
import com.cactus.bitacora.model.AreaOut
import com.cactus.bitacora.model.CatalogAreaOut
import com.cactus.bitacora.model.CatalogAssignmentOut
import com.cactus.bitacora.model.CatalogParticipantOut
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.OfflineCatalogOut
import com.cactus.bitacora.model.ParticipanteOut
import com.cactus.bitacora.model.ParticipantAdminIn
import com.cactus.bitacora.model.ParticipantAdminOut
import com.cactus.bitacora.model.ParticipantAdminPage
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceCatalogRepositoryTest {
    @Test
    fun supervisorModeResolvesCargoTwoOnceAndIncludesOwnAndDescendantAreas() = runBlocking {
        val dao = FakeCatalogDao().apply {
            participantTypes[1] = participantType(1, "operario")
            participantTypes[2] = participantType(2, "supervisor")
            participantTypes[3] = participantType(3, "directivo")
            participants[2] = participant(2, "P0002")
            participants[10] = participant(10, "P0010")
            participants[11] = participant(11, "P0011")
            participants[12] = participant(12, "P0012").copy(activo = false)
            areas[2] = area(2)
            areas[10] = area(10).copy(idPadre = 2)
            areas[20] = area(20)
            assignments[2 to 2] = assignment(2, 2, 2)
            assignments[10 to 10] = assignment(10, 10, 1)
            assignments[11 to 20] = assignment(11, 20, 1)
            assignments[12 to 10] = assignment(12, 10, 1)
        }
        val repository = repository(dao, FakeCatalogApi(failNetwork = true))

        assertEquals(2, repository.supervisorTypeCode())
        assertEquals(2, repository.localSupervisorSession(" p0002 ").id_supervisor)
        assertEquals(listOf(2, 10), repository.localSupervisedParticipants("P0002")
            .map { it.id_participante })
    }

    @Test
    fun participantMasterReturnsAll513RowsInNumericIdOrder() = runBlocking {
        val dao = FakeCatalogDao().apply {
            (513 downTo 1).forEach { id ->
                participants[id] = participant(id, "P${id.toString().padStart(4, '0')}")
            }
        }
        val repository = repository(dao, FakeCatalogApi())

        val page = repository.localAdminParticipants("")

        assertEquals(513, page.total)
        assertEquals(513, page.items.size)
        assertEquals((1..513).toList(), page.items.map { it.id_participante })
    }

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

    @Test
    fun catalogSnapshotMarksMissingRemoteRowsInactiveAndKeepsPendingLocals() = runBlocking {
        val dao = FakeCatalogDao().apply {
            participants[2] = participant(2, "P0002")
            participants[7] = participant(7, "P0007")
            participants[8] = participant(8, "P0008").copy(pendingAdminUpdate = true)
        }
        val api = FakeCatalogApi(snapshot = snapshot().copy(
            participantes = listOf(CatalogParticipantOut(2, "P0002", "Ana", "Prueba"))
        ))
        val repository = repository(dao, api)

        repository.syncCatalogs()

        assertEquals(true, dao.participants[2]!!.activo)
        assertEquals(false, dao.participants[7]!!.activo)
        assertEquals(true, dao.participants[8]!!.activo)
        assertEquals(2, dao.participantCount())
    }

    @Test
    fun participantRefreshReplacesFakeLocalNameWithServerValue() = runBlocking {
        val dao = FakeCatalogDao().apply {
            participants[1] = participant(1, "P0001").copy(
                nombre = "Nombre1", apellido = "Apellido1", documento = "000001"
            )
        }
        val realSnapshot = snapshot().copy(
            participantes = listOf(
                CatalogParticipantOut(1, "P0001", "ALVARO", "BERNAL BAYONA ALVARO", "1042592")
            )
        )
        val result = repository(dao, FakeCatalogApi(snapshot = realSnapshot)).syncParticipants()
        assertTrue(result.success)
        assertEquals(1, result.participants)
        assertEquals("ALVARO", dao.participants[1]!!.nombre)
        assertEquals("1042592", dao.participants[1]!!.documento)
    }

    @Test
    fun failedParticipantRefreshPreservesLocalCatalog() = runBlocking {
        val dao = FakeCatalogDao().apply {
            participants[1] = participant(1, "P0001").copy(nombre = "Nombre1")
        }
        val result = repository(dao, FakeCatalogApi(failNetwork = true)).syncParticipants()
        assertEquals(false, result.success)
        assertEquals("Nombre1", dao.participants[1]!!.nombre)
    }

    @Test
    fun offlineParticipantUpdatePreservesIdAndOtherRows() = runBlocking {
        val dao = FakeCatalogDao().apply {
            participants[2] = participant(2, "P0002")
            participants[3] = participant(3, "P0003")
        }
        val repository = repository(dao, FakeCatalogApi(failNetwork = true))
        val result = repository.updateAdminParticipantLocal(
            2, ParticipantAdminIn(1, "123", "P0002", "Patricia editada", null,
                null, null, null, null, "Observación local")
        )
        assertEquals(2, result.id_participante)
        assertEquals("Patricia editada", result.nombre)
        assertEquals("Nombre", dao.participants[3]!!.nombre)
        assertEquals(2, dao.participants.size)
        assertTrue(dao.participants[2]!!.pendingAdminUpdate)

        val restarted = repository(dao, FakeCatalogApi(failNetwork = true))
        assertEquals("Patricia editada", restarted.localAdminParticipants("", 0, 50).items
            .first { it.id_participante == 2 }.nombre)
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
            "getAdminParticipants" -> {
                val items = snapshot?.participantes.orEmpty().map {
                    ParticipantAdminOut(
                        it.id_participante, 1, it.documento.orEmpty(),
                        it.identificacion_participante, it.nombre.orEmpty(), it.apellido,
                        null, null, null, null, null, null, it.activo
                    )
                }
                ParticipantAdminPage(items, items.size, 0, 100)
            }
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
    val calendar = linkedMapOf<Long, com.cactus.bitacora.data.local.CalendarioGeneralLocalEntity>()
    val participants = linkedMapOf<Int, ParticipanteLocalEntity>()
    val areas = linkedMapOf<Int, AreaAdministrativaLocalEntity>()
    val assignments = linkedMapOf<Pair<Int, Int>, EmpleadoAreaLocalEntity>()
    val participantTypes =
        linkedMapOf<Int, com.cactus.bitacora.data.local.TipoParticipanteLocalEntity>()
    val workSchedules = linkedMapOf<Long, WorkScheduleLocalEntity>()
    val scheduleDetails = linkedMapOf<Long, WorkScheduleDetailLocalEntity>()
    var state: CatalogSyncStateEntity? = null
    var preparationState: CatalogPreparationStateEntity? = null

    override suspend fun activeParticipants() =
        participants.values.filter { it.activo }.sortedBy { it.idParticipante }

    override suspend fun workSchedules(): List<WorkScheduleLocalEntity> =
        workSchedules.values.filter { it.activo }.sortedBy { it.idJornada }

    override suspend fun workScheduleDetails(): List<WorkScheduleDetailLocalEntity> =
        scheduleDetails.values.sortedBy { it.idDetalle }

    override suspend fun participantByCode(code: String) =
        participants.values.firstOrNull { it.codigoQr == code }
    override suspend fun participantById(id: Int) = participants[id]
    override suspend fun areaById(id: Int) = areas[id]
    override suspend fun areaByCode(code: String) = areas.values.firstOrNull { it.codigoQr == code }
    override suspend fun activeAreas() =
        areas.values.filter { it.activo }.sortedBy { it.nombreArea }
    override suspend fun participantTypes() = participantTypes.values.toList()
    override suspend fun activeAssignments() = assignments.values.filter { it.activo }
    override suspend fun activeCalendar() = calendar.values.filter { it.activo }
    override suspend fun activeAssignmentsForParticipant(participantId: Int) =
        assignments.values.filter { it.idParticipante == participantId && it.activo }
    override suspend fun activeAssignmentsForArea(areaId: Int) =
        assignments.values.filter { it.idArea == areaId && it.activo }
    override suspend fun activeAssignment(participantId: Int, areaId: Int) =
        assignments[participantId to areaId]?.takeIf { it.activo }
    override suspend fun calendarDaysBetween(first: String, last: String): List<com.cactus.bitacora.data.local.CalendarioGeneralLocalEntity> =
        calendar.values.filter { it.activo && it.fechaInicio >= first && it.fechaInicio <= last }
    override suspend fun upsertParticipant(item: ParticipanteLocalEntity) {
        participants[item.idParticipante] = item
    }
    override suspend fun updateAdminParticipant(
        id: Int, tipoDocumento: Int, documento: String, codigo: String, nombre: String,
        apellido: String?, fechaNacimiento: String?, sexo: String?, fechaEntrada: String?,
        fechaSalida: String?, observaciones: String?, email: String?, activo: Boolean,
        pending: Boolean
    ): Int {
        val current = participants[id] ?: return 0
        participants[id] = current.copy(
            tipoDocumento = tipoDocumento, documento = documento, codigoQr = codigo,
            nombre = nombre, apellido = apellido, fechaNacimiento = fechaNacimiento,
            sexo = sexo, fechaEntrada = fechaEntrada, fechaSalida = fechaSalida,
            observaciones = observaciones, email = email, activo = activo,
            pendingAdminUpdate = pending
        )
        return 1
    }
    override suspend fun pendingAdminParticipants() = participants.values.filter { it.pendingAdminUpdate }
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
    override suspend fun upsertParticipantTypes(
        items: List<com.cactus.bitacora.data.local.TipoParticipanteLocalEntity>
    ) {
        items.forEach { participantTypes[it.codigo] = it }
    }
    override suspend fun upsertCalendar(items: List<com.cactus.bitacora.data.local.CalendarioGeneralLocalEntity>) {
        items.forEach { calendar[it.idPeriodo] = it }
    }
    override suspend fun upsertWorkSchedules(items: List<WorkScheduleLocalEntity>) {
        items.forEach { workSchedules[it.idJornada] = it }
    }
    override suspend fun upsertWorkScheduleDetails(items: List<WorkScheduleDetailLocalEntity>) {
        items.forEach { scheduleDetails[it.idDetalle] = it }
    }
    override suspend fun upsertPreparationState(state: CatalogPreparationStateEntity) {
        preparationState = state
    }
    override suspend fun deleteStaleWorkScheduleDetails(ids: List<Long>) {
        val allowed = ids.toHashSet()
        scheduleDetails.entries.filter { it.key !in allowed }.forEach { scheduleDetails.remove(it.key) }
    }
    override suspend fun deleteAllWorkScheduleDetails() {
        scheduleDetails.clear()
    }
    override suspend fun deleteStaleAssignments(ids: List<Int>) {
        val allowed = ids.toHashSet()
        assignments.entries.filter { it.value.idEmpleadoArea !in allowed }.forEach { assignments.remove(it.key) }
    }
    override suspend fun deleteAllAssignments() {
        assignments.clear()
    }
    override suspend fun applySnapshot(
        participants: List<ParticipanteLocalEntity>,
        areas: List<AreaAdministrativaLocalEntity>,
        assignments: List<EmpleadoAreaLocalEntity>,
        participantTypes: List<com.cactus.bitacora.data.local.TipoParticipanteLocalEntity>,
        workSchedules: List<WorkScheduleLocalEntity>,
        workScheduleDetails: List<WorkScheduleDetailLocalEntity>,
        state: CatalogSyncStateEntity,
        preparationState: CatalogPreparationStateEntity,
        calendar: List<com.cactus.bitacora.data.local.CalendarioGeneralLocalEntity>
    ) {
        upsertPreparationState(preparationState.copy(status = "PREPARING", preparedAtMillis = null))
        markAllParticipantsInactive()
        markAllAreasInactive()
        markAllAssignmentsInactive()
        markAllParticipantTypesInactive()
        markAllCalendarInactive()
        markAllWorkSchedulesInactive()
        upsertParticipants(participants)
        upsertAreas(areas)
        upsertAssignments(assignments)
        if (assignments.isEmpty()) deleteAllAssignments() else deleteStaleAssignments(assignments.map { it.idEmpleadoArea })
        upsertParticipantTypes(participantTypes)
        upsertWorkSchedules(workSchedules)
        upsertWorkScheduleDetails(workScheduleDetails)
        if (workScheduleDetails.isEmpty()) deleteAllWorkScheduleDetails() else deleteStaleWorkScheduleDetails(workScheduleDetails.map { it.idDetalle })
        upsertCalendar(calendar)
        upsertSyncState(state)
        upsertPreparationState(preparationState)
    }
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
    override suspend fun markAllParticipantTypesInactive() {
        participantTypes.replaceAll { _, value -> value.copy(activo = false) }
    }
    override suspend fun markAllCalendarInactive() {
        calendar.replaceAll { _, value -> value.copy(activo = false) }
    }
    override suspend fun markAllWorkSchedulesInactive() {
        workSchedules.replaceAll { _, value -> value.copy(activo = false) }
    }
    override suspend fun participantCount() = participants.values.count { it.activo }
    override suspend fun areaCount() = areas.values.count { it.activo }
    override suspend fun assignmentCount() = assignments.values.count { it.activo }
    override suspend fun participantTypeCount() =
        participantTypes.values.count { it.activo }
    override suspend fun calendarCount() = calendar.values.count { it.activo }
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

private fun participantType(code: Int, description: String) =
    com.cactus.bitacora.data.local.TipoParticipanteLocalEntity(
        code, description, "EMPLEADO", true, 1
    )

private fun snapshot() = OfflineCatalogOut(
    generated_at = "2026-07-19T00:00:00Z",
    participantes = listOf(CatalogParticipantOut(2, "P0002", "Ana", "Prueba")),
    areas = listOf(CatalogAreaOut(7, "Administración")),
    empleado_areas = listOf(CatalogAssignmentOut(2, 7, 1))
)
