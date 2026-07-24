package com.cactus.bitacora.data

import android.content.Context
import com.cactus.bitacora.data.local.AreaAdministrativaLocalEntity
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.CatalogSyncStateEntity
import com.cactus.bitacora.data.local.EmpleadoAreaLocalEntity
import com.cactus.bitacora.data.local.ParticipanteLocalEntity
import com.cactus.bitacora.data.local.ReferenceCatalogDao
import com.cactus.bitacora.model.AreaOut
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

data class CatalogSyncResult(
    val success: Boolean,
    val participants: Int,
    val areas: Int,
    val assignments: Int,
    val error: String? = null,
    val retryable: Boolean = false
)

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

    suspend fun syncCatalogs(): CatalogSyncResult {
        return try {
            val remote = api.getOfflineCatalogs()
            val now = System.currentTimeMillis()
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
                )
            }
            val areas = remote.areas.map {
                AreaAdministrativaLocalEntity(
                    idArea = it.id_area,
                    codigoQr = canonicalAreaCode(it.id_area, it.descripcion),
                    nombreArea = it.descripcion,
                    activo = it.activo,
                    updatedAtServer = it.updated_at,
                    syncedAtMillis = now
                )
            }
            val assignments = remote.empleado_areas.map {
                EmpleadoAreaLocalEntity(
                    idParticipante = it.id_participante,
                    idArea = it.id_area,
                    cargo = it.cargo,
                    fechaFinal = it.fecha_final,
                    activo = it.activo,
                    updatedAtServer = it.updated_at,
                    syncedAtMillis = now
                )
            }
            dao.applySnapshot(
                participants,
                areas,
                assignments,
                CatalogSyncStateEntity(
                    lastSuccessfulSyncMillis = now,
                    participantCount = participants.count { it.activo },
                    areaCount = areas.count { it.activo },
                    assignmentCount = assignments.count { it.activo }
                )
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

    private suspend fun cacheAssignments(items: List<EmpleadoAreaActivaOut>, now: Long) {
        items.forEach {
            dao.upsertAssignment(
                EmpleadoAreaLocalEntity(
                    it.id_participante,
                    it.id_area,
                    it.cargo,
                    it.fecha_final,
                    true,
                    null,
                    now
                )
            )
            it.area_descripcion?.let { name ->
                dao.upsertArea(
                    AreaAdministrativaLocalEntity(
                        it.id_area,
                        canonicalAreaCode(it.id_area, name),
                        name,
                        true,
                        null,
                        now
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
    id_area,
    ReferenceCatalogRepository.canonicalAreaCode(id_area, descripcion),
    descripcion,
    true,
    null,
    now
)

private fun EmpleadoAreaLocalEntity.toApi() = EmpleadoAreaActivaOut(
    idParticipante,
    idArea,
    null,
    cargo,
    fechaFinal
)
