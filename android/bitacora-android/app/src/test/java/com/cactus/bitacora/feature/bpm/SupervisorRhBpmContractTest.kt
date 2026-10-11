package com.cactus.bitacora.feature.bpm

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisorRhBpmContractTest {
    private val projectRoot = File(System.getProperty("user.dir"))

    @Test
    fun `permiso tipo 7 captura y envia ambas horas`() {
        val screen = projectRoot.resolve("src/main/java/com/cactus/bitacora/feature/bpm/SupervisorRhBpmScreen.kt").readText()
        assertTrue(screen.contains("Hora inicial HH:MM"))
        assertTrue(screen.contains("Hora final HH:MM"))
        assertTrue(screen.contains("startTime.takeIf { noveltyType == 7 }"))
        assertTrue(screen.contains("endTime.takeIf { noveltyType == 7 }"))
    }

    @Test
    fun `seleccion superior a veinte evidencias no se trunca silenciosamente`() {
        val screen = projectRoot.resolve("src/main/java/com/cactus/bitacora/feature/bpm/SupervisorRhBpmScreen.kt").readText()
        assertTrue(screen.contains("if (uris.size > 20)"))
        assertTrue(screen.contains("Se permiten maximo 20 evidencias"))
    }

    @Test
    fun `repositorio valida fechas y horas antes de guardar en Room`() {
        val repository = projectRoot.resolve("src/main/java/com/cactus/bitacora/feature/bpm/BpmRepository.kt").readText()
        assertTrue(repository.contains("LocalDate.parse(it.startDate)"))
        assertTrue(repository.contains("LocalTime.parse(it.startTime)"))
        assertTrue(repository.contains("Las horas son obligatorias para una novedad de permiso"))
    }

    @Test
    fun `conflicto conserva operacion pero elimina solo sucesor provisional`() {
        val repository = projectRoot.resolve("src/main/java/com/cactus/bitacora/feature/bpm/BpmRepository.kt").readText()
        val entities = projectRoot.resolve("src/main/java/com/cactus/bitacora/data/local/BpmLocalEntities.kt").readText()
        assertTrue(repository.contains("dao.removeUnconfirmedSuccessor(it)"))
        assertTrue(entities.contains("provisional=1 AND remoteId IS NULL"))
        assertTrue(repository.contains("dao.removeTerminalConflictSuccessors()"))
        assertTrue(entities.contains("o.syncState IN ('CONFLICT','REJECTED')"))
    }

    @Test
    fun `todos los participantes identificados acceden a tareas y c33 conserva pantalla especializada`() {
        val mode = projectRoot.resolve("src/main/java/com/cactus/bitacora/SupervisorModeScreen.kt").readText()
        val main = projectRoot.resolve("src/main/java/com/cactus/bitacora/MainActivity.kt").readText()
        val worker = projectRoot.resolve("src/main/java/com/cactus/bitacora/WorkerModeScreen.kt").readText()
        val management = projectRoot.resolve("src/main/java/com/cactus/bitacora/ManagementModeScreen.kt").readText()
        val inbox = projectRoot.resolve("src/main/java/com/cactus/bitacora/feature/bpm/ParticipantBpmTasksScreen.kt").readText()
        assertTrue(mode.contains("isSupervisorRh"))
        assertTrue(mode.contains("Text(\"Tareas BPM\")"))
        assertTrue(mode.contains("ParticipantBpmTasksScreen"))
        assertTrue(mode.contains("SupervisorRhBpmScreen"))
        assertTrue(worker.contains("ParticipantBpmTasksScreen(active.codigo)"))
        assertTrue(management.contains("ParticipantBpmTasksScreen(active.codigo)"))
        assertTrue(inbox.contains("No tiene tareas pendientes"))
        assertTrue(inbox.contains("repository.tasks(actorCode)"))
        assertTrue(main.contains("SUPERVISOR / SUPERVISOR RH"))
    }
}
