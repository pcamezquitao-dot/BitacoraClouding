package com.cactus.bitacora.data

import android.content.Context
import com.cactus.bitacora.data.local.AreaAdministrativaLocalEntity
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.CatalogSyncStateEntity
import com.cactus.bitacora.data.local.CatalogPreparationStateEntity
import com.cactus.bitacora.data.local.CalendarioGeneralLocalEntity
import com.cactus.bitacora.data.local.EmpleadoAreaLocalEntity
import com.cactus.bitacora.data.local.ParticipanteLocalEntity
import com.cactus.bitacora.data.local.ReferenceCatalogDao
import com.cactus.bitacora.data.local.TipoParticipanteLocalEntity
import com.cactus.bitacora.model.AreaOut
import com.cactus.bitacora.model.AreaTreeNodeOut
import com.cactus.bitacora.model.CalendarTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaAssignmentOut
import com.cactus.bitacora.model.EmployeeAreaTreeNodeOut
import com.cactus.bitacora.model.ParticipantAdminOut
import com.cactus.bitacora.model.ParticipantAdminPage
import com.cactus.bitacora.model.ParticipantOptionOut
import com.cactus.bitacora.model.ParticipantTypeAdminOut
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.ParticipanteOut
import java.io.IOException
import retrofit2.HttpException

enum class CatalogRole { EMPLOYEE, SUPERVISOR }

data class ParsedAreaCode(val id: Int, val suppliedName: String?)

data class CatalogStatus(
    val participants: Int,
    val areas: Int,
    val assignments: Int,
    val lastSuccessfulSyncMillis: Long?,
    val lastError: String?
)

data class CatalogAreaOption(
    val area: AreaOut,
    val qr: String
)

data class CatalogSyncResult(
    val success: Boolean,
    val participants: Int,
    val areas: Int,
    val assignments: Int,
    val error: String? = null,
    val retryable: Boolean = false
)

data class ParticipantUpdateResult(
    val participant: ParticipantAdminOut,
    val synced: Boolean,
    val syncError: String? = null
)

internal const val SUPERVISOR_TYPE_NAME = "supervisor"

class CatalogValidationException(message: String) : IllegalStateException(message)

internal fun normalizeCatalogCode(raw: String): String = raw.trim().uppercase()

internal fun parseAreaCode(raw: String): ParsedAreaCode {
    val parts = raw.split("|").map(String::trim)
    if (parts.size < 2 || parts[0].uppercase().replace(" ", "_") != "AREA_ADMINISTRATIVA") {
        throw CatalogValidationException(
            "QR de área inválido. Formato esperado: AREA_ADMINISTRATIVA|ID|NOMBRE"
        )
    }
    val id = parts[1].toIntOrNull()
        ?: throw CatalogValidationException("El identificador del área no es válido")
    return ParsedAreaCode(id, parts.getOrNull(2)?.takeIf(String::isNotBlank))
}

internal fun assignmentAllowsRole(cargo: Int?, role: CatalogRole): Boolean = when (role) {
    CatalogRole.SUPERVISOR -> cargo == 3
    CatalogRole.EMPLOYEE -> cargo != 3 && cargo != 4
}

class ReferenceCatalogRepository(
    context: Context?,
    private val api: BitacoraApi,
    private val dao: ReferenceCatalogDao =
        BitacoraDatabase.getInstance(requireNotNull(context)).referenceCatalogDao()
) {
    suspend fun supervisorTypeCode(): Int {
        val matches = dao.participantTypes().filter {
            it.activo && it.descripcion.trim().equals(SUPERVISOR_TYPE_NAME, ignoreCase = true)
        }
        if (matches.size != 1) throw CatalogValidationException(
            "El catálogo debe contener exactamente un tipo activo llamado supervisor"
        )
        return matches.single().codigo
    }

    suspend fun localSupervisorSession(rawCode: String): com.cactus.bitacora.model.SupervisorSessionOut {
        val code = normalizeCatalogCode(rawCode)
        val participant = dao.participantByCode(code)
            ?: throw CatalogValidationException("El supervisor no está en el catálogo local")
        if (!participant.activo) throw CatalogValidationException("El supervisor está inactivo")
        val supervisorType = supervisorTypeCode()
        val assignments = dao.activeAssignmentsForParticipant(participant.idParticipante)
            .filter { it.cargo == supervisorType }
        if (assignments.isEmpty()) throw CatalogValidationException(
            "El participante no tiene cargo supervisor vigente"
        )
        val areas = assignments.mapNotNull { assignment ->
            dao.areaById(assignment.idArea)?.takeIf { it.activo }?.let {
                com.cactus.bitacora.model.SupervisorAreaOut(it.idArea, it.nombreArea)
            }
        }
        return com.cactus.bitacora.model.SupervisorSessionOut(
            participant.idParticipante, participant.codigoQr,
            listOfNotNull(participant.nombre, participant.apellido).joinToString(" "),
            "Supervisor identificado", areas
        )
    }

    suspend fun localSupervisedParticipants(rawCode: String): List<com.cactus.bitacora.model.SupervisedParticipantOut> {
        val session = localSupervisorSession(rawCode)
        val allAreas = dao.activeAreas()
        val allowed = session.areas.mapTo(mutableSetOf()) { it.id_area }
        do {
            val before = allowed.size
            allAreas.filter { it.idPadre in allowed }.forEach { allowed += it.idArea }
        } while (allowed.size != before)
        val areas = allAreas.associateBy { it.idArea }
        val people = dao.activeParticipants().associateBy { it.idParticipante }
        return dao.activeAssignments().filter { it.idArea in allowed }.mapNotNull { assignment ->
            val person = people[assignment.idParticipante] ?: return@mapNotNull null
            val area = areas[assignment.idArea] ?: return@mapNotNull null
            com.cactus.bitacora.model.SupervisedParticipantOut(
                person.idParticipante, person.codigoQr, person.nombre, person.apellido,
                area.idArea, area.nombreArea
            )
        }.distinctBy { it.id_participante to it.id_area }
            .sortedWith(compareBy({ it.id_participante }, { it.id_area }))
    }

    suspend fun syncParticipants(): CatalogSyncResult {
        return try {
            val remoteParticipants = mutableListOf<ParticipantAdminOut>()
            var offset = 0
            do {
                val page = api.getAdminParticipants("", offset, PARTICIPANT_PAGE_SIZE)
                remoteParticipants += page.items
                offset += page.items.size
            } while (page.items.isNotEmpty() && offset < page.total)
            if (remoteParticipants.isEmpty()) {
                throw CatalogValidationException("El servidor devolvió el catálogo participante vacío")
            }
            val now = System.currentTimeMillis()
            val pending = dao.pendingAdminParticipants().associateBy { it.idParticipante }
            val participants = remoteParticipants.map {
                ParticipanteLocalEntity(
                    idParticipante = it.id_participante,
                    codigoQr = normalizeCatalogCode(it.identificacion_participante),
                    nombre = it.nombre,
                    apellido = it.apellido,
                    documento = it.documento,
                    activo = it.activo,
                    updatedAtServer = null,
                    syncedAtMillis = now,
                    tipoDocumento = it.tipo_documento,
                    fechaNacimiento = it.fecha_nacimiento,
                    sexo = it.sexo,
                    fechaEntrada = it.fecha_entrada,
                    fechaSalida = it.fecha_salida,
                    observaciones = it.observaciones,
                    email = it.email
                ).let { fresh -> pending[fresh.idParticipante] ?: fresh }
            }
            dao.upsertParticipants(participants)
            CatalogSyncResult(true, participants.size, dao.areaCount(), dao.assignmentCount())
        } catch (error: Exception) {
            val status = status()
            CatalogSyncResult(
                false, status.participants, status.areas, status.assignments,
                safeCatalogError(error), error is IOException ||
                    error is HttpException && error.code() >= 500
            )
        }
    }

    suspend fun syncCatalogs(): CatalogSyncResult {
        return try {
            val remote = api.getOfflineCatalogs()
            val now = System.currentTimeMillis()
            val pendingParticipants = dao.pendingAdminParticipants().associateBy { it.idParticipante }
            val participants = remote.participantes.map {
                ParticipanteLocalEntity(
                    idParticipante = it.id_participante,
                    codigoQr = normalizeCatalogCode(it.identificacion_participante),
                    nombre = it.nombre,
                    apellido = it.apellido,
                    documento = it.documento,
                    activo = it.activo,
                    updatedAtServer = it.updated_at,
                    syncedAtMillis = now
                ).let { fresh -> pendingParticipants[fresh.idParticipante] ?: fresh }
            } + pendingParticipants.values.filter { pending ->
                remote.participantes.none { it.id_participante == pending.idParticipante }
            }
            val areas = remote.areas.map {
                AreaAdministrativaLocalEntity(
                    idArea = it.id_area,
                    codigoQr = canonicalAreaCode(it.id_area, it.descripcion),
                    nombreArea = it.descripcion,
                    nombreCorto = it.nombre_corto,
                    idPadre = it.nodo_padre,
                    activo = it.activo,
                    updatedAtServer = it.updated_at,
                    syncedAtMillis = now
                )
            }
            val assignments = remote.empleado_areas.map {
                EmpleadoAreaLocalEntity(
                    idEmpleadoArea = it.id_empleado_area,
                    idParticipante = it.id_participante,
                    idArea = it.id_area,
                    cargo = it.cargo,
                    fechaInicia = it.fecha_inicia,
                    fechaFinal = it.fecha_final,
                    activo = it.activo,
                    updatedAtServer = it.updated_at,
                    syncedAtMillis = now
                )
            }
            val participantTypes = remote.tipos_participante.map {
                TipoParticipanteLocalEntity(
                    codigo = it.codigo,
                    descripcion = it.descripcion,
                    capacidadesCsv = it.capacidades
                        .map(String::uppercase)
                        .distinct()
                        .sorted()
                        .joinToString(","),
                    activo = it.activo,
                    syncedAtMillis = now
                )
            }
            if (participants.isEmpty() || areas.isEmpty()) {
                throw CatalogValidationException("El servidor devolvió un catálogo incompleto")
            }
            val participantIds = participants.mapTo(hashSetOf()) { it.idParticipante }
            val areaIds = areas.mapTo(hashSetOf()) { it.idArea }
            if (assignments.any { it.idParticipante !in participantIds || it.idArea !in areaIds }) {
                throw CatalogValidationException("El servidor devolvió relaciones incompletas")
            }
            val calendar = remote.calendario.map {
                CalendarioGeneralLocalEntity(
                    it.id_periodo, it.id_padre, it.nivel, it.codigo, it.nombre,
                    it.fecha_inicio, it.fecha_fin, it.numero_dia_semana,
                    it.nombre_dia_semana, it.es_fin_semana, it.es_festivo,
                    it.nombre_festivo, it.activo, now
                )
            }
            val syncState = CatalogSyncStateEntity(
                lastSuccessfulSyncMillis = now,
                participantCount = participants.count { it.activo },
                areaCount = areas.count { it.activo },
                assignmentCount = assignments.count { it.activo },
                participantTypeCount = participantTypes.count { it.activo },
                calendarCount = calendar.count { it.activo }
            )
            val preparationState = CatalogPreparationStateEntity(
                status = "READY",
                preparedAtMillis = now,
                assignmentCount = assignments.count { it.activo },
                nullScheduleCount = 0,
                orphanScheduleCount = 0,
                validationMessage = null
            )
            dao.applySnapshot(
                participants = participants,
                areas = areas,
                assignments = assignments,
                participantTypes = participantTypes,
                workSchedules = emptyList(),
                workScheduleDetails = emptyList(),
                state = syncState,
                preparationState = preparationState,
                calendar = calendar
            )
            CatalogSyncResult(true, participants.size, areas.size, assignments.size)
        } catch (error: Exception) {
            val previous = dao.syncState()
            val message = safeCatalogError(error)
            dao.upsertSyncState(
                (previous ?: CatalogSyncStateEntity(lastSuccessfulSyncMillis = null))
                    .copy(lastError = message)
            )
            val status = status()
            CatalogSyncResult(
                false,
                status.participants,
                status.areas,
                status.assignments,
                message,
                retryable = error is IOException ||
                    (error is HttpException &&
                        (error.code() == 408 || error.code() == 429 || error.code() >= 500))
            )
        }
    }

    suspend fun participantByCode(raw: String): ParticipanteOut {
        val code = normalizeCatalogCode(raw)
        if (code.isBlank()) throw CatalogValidationException("Debe ingresar el código del participante")
        dao.participantByCode(code)?.let {
            if (!it.activo) throw CatalogValidationException("El participante está inactivo")
            return it.toApi()
        }
        return try {
            api.getParticipanteByQr(code).also { participant ->
                dao.upsertParticipant(participant.toLocal(System.currentTimeMillis()))
            }
        } catch (error: Exception) {
            if (error is HttpException && error.code() == 404) {
                throw CatalogValidationException("El participante no existe")
            }
            throw CatalogValidationException(MISSING_LOCAL_MESSAGE)
        }
    }

    suspend fun localWorkerSession(raw: String): com.cactus.bitacora.model.WorkerSessionOut {
        val participant = participantByCode(raw)
        if (assignmentsForParticipant(participant.id_participante).isEmpty()) {
            throw CatalogValidationException("El participante no tiene una asignación activa como trabajador")
        }
        return com.cactus.bitacora.model.WorkerSessionOut(
            participant.id_participante,
            participant.identificacion_participante.orEmpty(),
            listOfNotNull(participant.nombre, participant.apellido).joinToString(" ").trim()
        )
    }

    suspend fun localWorkerCalendar(
        year: Int,
        month: Int
    ): List<com.cactus.bitacora.model.WorkerDayOut> {
        val first = java.time.LocalDate.of(year, month, 1)
        return dao.calendarDaysBetween(first.toString(), first.withDayOfMonth(first.lengthOfMonth()).toString())
            .map { day ->
                val weekend = day.esFinSemana == true
                com.cactus.bitacora.model.WorkerDayOut(
                    day.fechaInicio,
                    day.numeroDiaSemana ?: java.time.LocalDate.parse(day.fechaInicio).dayOfWeek.value,
                    !weekend && !day.esFestivo,
                    day.numeroDiaSemana == 6,
                    day.numeroDiaSemana == 7,
                    day.esFestivo,
                    day.nombreFestivo,
                    0,
                    false,
                    emptyList()
                )
            }
    }

    suspend fun assignmentsForParticipant(participantId: Int): List<EmpleadoAreaActivaOut> {
        val local = dao.activeAssignmentsForParticipant(participantId)
        if (local.isNotEmpty()) return local.map { it.toApi() }
        return try {
            api.getAsignacionesActivas(participantId).also {
                cacheAssignments(it, System.currentTimeMillis())
            }
        } catch (error: Exception) {
            if (error is HttpException && error.code() == 404) emptyList()
            else throw CatalogValidationException(MISSING_LOCAL_MESSAGE)
        }
    }

    suspend fun participantById(participantId: Int): ParticipanteOut =
        dao.participantById(participantId)?.toApi()
            ?: throw CatalogValidationException(
                "El supervisor no está disponible en el catálogo local"
            )

    suspend fun activeAreas(): List<CatalogAreaOption> =
        dao.activeAreas().map { CatalogAreaOption(it.toApi(), it.codigoQr) }

    suspend fun assignmentForRole(
        participantId: Int,
        role: CatalogRole
    ): EmpleadoAreaActivaOut {
        return assignmentsForParticipant(participantId)
            .firstOrNull { assignmentAllowsRole(it.cargo, role) }
            ?: throw CatalogValidationException(
                if (role == CatalogRole.SUPERVISOR) {
                    "El participante existe, pero no tiene rol activo de supervisor"
                } else {
                    "El participante existe, pero no tiene rol activo de empleado"
                }
            )
    }

    suspend fun areaByQr(raw: String): AreaOut {
        val parsed = parseAreaCode(raw)
        dao.areaById(parsed.id)?.let {
            if (!it.activo) throw CatalogValidationException("El área está inactiva")
            return it.toApi()
        }
        return try {
            api.getAreaByQr(com.cactus.bitacora.data.models.AreaByQrIn(raw)).also {
                dao.upsertArea(it.toLocal(System.currentTimeMillis()))
            }
        } catch (error: Exception) {
            if (error is HttpException && error.code() == 404) {
                throw CatalogValidationException("El área administrativa no existe")
            }
            throw CatalogValidationException(MISSING_LOCAL_MESSAGE)
        }
    }

    suspend fun requireActiveAssignment(participantId: Int, areaId: Int) {
        if (dao.activeAssignment(participantId, areaId) != null) return
        val assignments = assignmentsForParticipant(participantId)
        if (assignments.none { it.id_area == areaId }) {
            throw CatalogValidationException(
                "El participante existe, pero no está asignado al área seleccionada"
            )
        }
    }

    suspend fun status(): CatalogStatus = CatalogStatus(
        participants = dao.participantCount(),
        areas = dao.areaCount(),
        assignments = dao.assignmentCount(),
        lastSuccessfulSyncMillis = dao.syncState()?.lastSuccessfulSyncMillis,
        lastError = dao.syncState()?.lastError
    )

    suspend fun localAdminParticipants(search: String, offset: Int, limit: Int): ParticipantAdminPage {
        val needle = search.trim().lowercase()
        val all = dao.activeParticipants().filter { item ->
            needle.isBlank() || listOf(item.codigoQr, item.nombre, item.apellido, item.documento)
                .any { it?.lowercase()?.contains(needle) == true }
        }
        return ParticipantAdminPage(all.drop(offset).take(limit).map {
            it.toAdminOut()
        }, all.size, offset, limit)
    }

    suspend fun localAdminParticipants(search: String): ParticipantAdminPage {
        val needle = search.trim().lowercase()
        val all = dao.activeParticipants().filter { item ->
            needle.isBlank() || listOf(item.codigoQr, item.nombre, item.apellido, item.documento)
                .any { it?.lowercase()?.contains(needle) == true }
        }
        return ParticipantAdminPage(all.map { it.toAdminOut() }, all.size, 0, all.size)
    }

    suspend fun updateAdminParticipantLocal(
        id: Int,
        payload: com.cactus.bitacora.model.ParticipantAdminIn,
        synced: Boolean = false
    ): ParticipantAdminOut {
        val current = dao.participantById(id)
            ?: throw CatalogValidationException("El participante seleccionado no existe en SQLite")
        val normalized = payload.copy(
            documento = payload.documento.trim(),
            identificacion_participante = normalizeCatalogCode(payload.identificacion_participante),
            nombre = payload.nombre.trim(),
            apellido = payload.apellido?.trim()?.ifBlank { null },
            fecha_nacimiento = payload.fecha_nacimiento?.trim()?.ifBlank { null },
            sexo = payload.sexo?.trim()?.uppercase()?.ifBlank { null },
            fecha_entrada = payload.fecha_entrada?.trim()?.ifBlank { null },
            fecha_salida = payload.fecha_salida?.trim()?.ifBlank { null },
            observaciones = payload.observaciones?.trim()?.ifBlank { null },
            email = payload.email?.trim()?.ifBlank { null }
        )
        val affected = dao.updateAdminParticipant(
            id, normalized.tipo_documento, normalized.documento,
            normalized.identificacion_participante, normalized.nombre, normalized.apellido,
            normalized.fecha_nacimiento, normalized.sexo, normalized.fecha_entrada,
            normalized.fecha_salida, normalized.observaciones, normalized.email,
            current.activo, !synced
        )
        if (affected != 1) throw CatalogValidationException(
            "SQLite no actualizó exactamente al participante seleccionado"
        )
        return dao.participantById(id)!!.toAdminOut()
    }

    suspend fun markAdminParticipantSynced(id: Int, remote: ParticipantAdminOut): ParticipantAdminOut {
        return updateAdminParticipantLocal(id, remote.toInput(), synced = true)
    }

    suspend fun pendingAdminParticipantUpdates(): List<Pair<Int, com.cactus.bitacora.model.ParticipantAdminIn>> =
        dao.pendingAdminParticipants().map { it.idParticipante to it.toAdminOut().toInput() }

    suspend fun localAreaTree(): List<AreaTreeNodeOut> {
        val rows = dao.activeAreas()
        val byId = rows.associateBy { it.idArea }
        fun ancestors(item: AreaAdministrativaLocalEntity): List<AreaAdministrativaLocalEntity> {
            val result = mutableListOf<AreaAdministrativaLocalEntity>()
            var parent = item.idPadre
            val seen = mutableSetOf<Int>()
            while (parent != null && parent != 0 && seen.add(parent)) {
                val node = byId[parent] ?: break
                result.add(0, node); parent = node.idPadre
            }
            return result
        }
        return rows.map { item ->
            val parents = ancestors(item)
            AreaTreeNodeOut(item.idArea, item.nombreArea, item.nombreCorto, item.idPadre,
                parents.size, (parents.map { it.nombreArea } + item.nombreArea).joinToString(" / "))
        }
    }

    suspend fun localParticipantTypes(): List<ParticipantTypeAdminOut> =
        dao.participantTypes().map { ParticipantTypeAdminOut(it.codigo, it.descripcion, it.activo, it.capacidades.toList()) }

    suspend fun localParticipantOptions(): List<ParticipantOptionOut> = dao.activeParticipants().map {
        val fullName = listOfNotNull(it.nombre, it.apellido).joinToString(" ").trim()
        ParticipantOptionOut(it.idParticipante, it.codigoQr, it.nombre.orEmpty(), it.apellido.orEmpty(), fullName, it.documento)
    }

    suspend fun localEmployeeAreaTree(): List<EmployeeAreaTreeNodeOut> {
        val areas = localAreaTree()
        val people = dao.activeParticipants().associateBy { it.idParticipante }
        val types = dao.participantTypes().associateBy { it.codigo }
        val assignments = dao.activeAssignments().groupBy { it.idArea }
        fun build(area: AreaTreeNodeOut): EmployeeAreaTreeNodeOut {
            val assigned = assignments[area.id_area].orEmpty().map { item ->
                val person = people[item.idParticipante]
                EmployeeAreaAssignmentOut(
                    item.idEmpleadoArea,
                    item.idParticipante,
                    person?.codigoQr.orEmpty(),
                    listOfNotNull(person?.nombre, person?.apellido).joinToString(" "),
                    item.cargo,
                    types[item.cargo]?.descripcion,
                    null,
                    item.fechaInicia.orEmpty(),
                    item.fechaFinal,
                    item.idJornada
                )
            }
            val children = areas.filter { it.id_padre == area.id_area }.map(::build)
            return EmployeeAreaTreeNodeOut(area.id_area, area.descripcion, area.nombre_corto, area.id_padre,
                area.nivel, area.ruta, assigned.size, assigned, children)
        }
        val ids = areas.mapTo(hashSetOf()) { it.id_area }
        return areas.filter { it.id_padre == null || it.id_padre == 0 || it.id_padre !in ids }.map(::build)
    }

    suspend fun localCalendarTree(): List<CalendarTreeNodeOut> {
        val rows = dao.activeCalendar()
        fun node(item: CalendarioGeneralLocalEntity): CalendarTreeNodeOut = CalendarTreeNodeOut(
            item.idPeriodo, item.idPadre, item.nivel, item.codigo, item.nombre, item.fechaInicio,
            item.fechaFin, item.numeroDiaSemana, item.nombreDiaSemana, item.esFinSemana,
            item.esFestivo, item.nombreFestivo, rows.filter { it.idPadre == item.idPeriodo }.map(::node)
        )
        val ids = rows.mapTo(hashSetOf()) { it.idPeriodo }
        return rows.filter { it.idPadre == null || it.idPadre !in ids }.map(::node)
    }

    suspend fun cacheCalendarTree(nodes: List<CalendarTreeNodeOut>) {
        if (nodes.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = mutableListOf<CalendarioGeneralLocalEntity>()
        fun collect(items: List<CalendarTreeNodeOut>) {
            items.forEach {
                rows += CalendarioGeneralLocalEntity(
                    it.id_periodo, it.id_padre, it.nivel, it.codigo, it.nombre,
                    it.fecha_inicio, it.fecha_fin, it.numero_dia_semana,
                    it.nombre_dia_semana, it.es_fin_semana, it.es_festivo,
                    it.nombre_festivo, true, now
                )
                collect(it.hijos)
            }
        }
        collect(nodes)
        dao.upsertCalendar(rows)
    }

    private suspend fun cacheAssignments(items: List<EmpleadoAreaActivaOut>, now: Long) {
        items.forEach {
            dao.upsertAssignment(
                EmpleadoAreaLocalEntity(
                    idParticipante = it.id_participante,
                    idArea = it.id_area,
                    cargo = it.cargo,
                    fechaInicia = null,
                    fechaFinal = it.fecha_final,
                    activo = true,
                    updatedAtServer = null,
                    syncedAtMillis = now
                )
            )
            it.area_descripcion?.let { name ->
                dao.upsertArea(
                    AreaAdministrativaLocalEntity(
                        idArea = it.id_area,
                        codigoQr = canonicalAreaCode(it.id_area, name),
                        nombreArea = name,
                        nombreCorto = null,
                        idPadre = null,
                        activo = true,
                        updatedAtServer = null,
                        syncedAtMillis = now
                    )
                )
            }
        }
    }

    private fun safeCatalogError(error: Exception): String = when (error) {
        is HttpException -> "No fue posible actualizar los catálogos (HTTP ${error.code()})"
        is IOException -> "Sin conexión. Se conserva el catálogo local anterior"
        else -> "No fue posible actualizar los catálogos"
    }

    companion object {
        private const val PARTICIPANT_PAGE_SIZE = 100
        const val MISSING_LOCAL_MESSAGE =
            "No fue posible validar este código sin conexión porque todavía no está " +
                "almacenado en el catálogo local. Conéctese y actualice los datos de referencia."

        fun canonicalAreaCode(id: Int, name: String): String =
            "AREA_ADMINISTRATIVA|$id|${name.trim()}"
    }
}

private fun ParticipanteLocalEntity.toApi() = ParticipanteOut(
    idParticipante,
    nombre,
    apellido,
    codigoQr,
    documento
)

private fun ParticipanteLocalEntity.toAdminOut() = ParticipantAdminOut(
    id_participante = idParticipante,
    tipo_documento = tipoDocumento,
    documento = documento.orEmpty(),
    identificacion_participante = codigoQr,
    nombre = nombre.orEmpty(),
    apellido = apellido,
    fecha_nacimiento = fechaNacimiento,
    sexo = sexo,
    fecha_entrada = fechaEntrada,
    fecha_salida = fechaSalida,
    observaciones = observaciones,
    email = email,
    activo = activo
)

private fun ParticipantAdminOut.toInput() = com.cactus.bitacora.model.ParticipantAdminIn(
    tipo_documento, documento, identificacion_participante, nombre, apellido,
    fecha_nacimiento, sexo, fecha_entrada, fecha_salida, observaciones, email
)

private fun ParticipanteOut.toLocal(now: Long) = ParticipanteLocalEntity(
    id_participante,
    normalizeCatalogCode(identificacion_participante.orEmpty()),
    nombre,
    apellido,
    documento,
    true,
    null,
    now
)

private fun AreaAdministrativaLocalEntity.toApi() = AreaOut(idArea, nombreArea)

private fun AreaOut.toLocal(now: Long) = AreaAdministrativaLocalEntity(
    idArea = id_area,
    codigoQr = ReferenceCatalogRepository.canonicalAreaCode(id_area, descripcion),
    nombreArea = descripcion,
    nombreCorto = null,
    idPadre = null,
    activo = true,
    updatedAtServer = null,
    syncedAtMillis = now
)

private fun EmpleadoAreaLocalEntity.toApi() = EmpleadoAreaActivaOut(
    idParticipante,
    idArea,
    null,
    cargo,
    fechaFinal
)
