package com.cactus.bitacora.feature.testdata

import com.cactus.bitacora.model.ParticipanteOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class TestBitacoraGeneratorTest {
    private val parameters = TestBitacoraParameters(
        participantCode = " p0015 ",
        startDate = LocalDate.of(2026, 8, 9),
        endDate = LocalDate.of(2026, 8, 22),
        seed = 220826L
    )

    @Test
    fun `period with twelve ordinary days and one selected Sunday produces 26 records`() {
        val batch = TestBitacoraGenerator.generate(parameters)

        assertEquals(13, batch.workdays.size)
        assertEquals(1, batch.sundayCount)
        assertEquals(13, batch.entryCount)
        assertEquals(13, batch.exitCount)
        assertEquals(26, batch.physicalRecordCount)
    }

    @Test
    fun `entry and exit requests follow the physical row convention`() {
        val preview = preview(TestBitacoraGenerator.generate(parameters))
        val requests = preview.toCreateRequests()

        requests.chunked(2).forEach { (entry, exit) ->
            assertEquals(4, entry.tipo_anotacion)
            assertNull(entry.ts_out_min)
            assertEquals(5, exit.tipo_anotacion)
            assertEquals(exit.ts_in_min, exit.ts_out_min)
            assertTrue(requireNotNull(entry.ts_in_min) < requireNotNull(exit.ts_in_min))
            assertEquals(TEST_BITACORA_ORIGIN, entry.origen_bitacora)
            assertEquals(TEST_BITACORA_ORIGIN, exit.origen_bitacora)
            assertEquals("DATOS_PRUEBA_P0015", entry.observaciones)
            assertEquals("DATOS_PRUEBA_P0015", exit.observaciones)
            assertNotEquals(entry.client_uuid, exit.client_uuid)
        }
        assertEquals(26, requests.map { it.client_uuid }.toSet().size)
    }

    @Test
    fun `mean deviation bounds and entry variation are respected`() {
        val batch = TestBitacoraGenerator.generate(parameters)

        assertEquals(480.0, batch.meanMinutes, 0.01)
        assertEquals(60.0, requireNotNull(batch.sampleDeviationMinutes), 2.0)
        batch.workdays.forEach {
            assertTrue(it.durationMinutes in 300..660)
            val localEntry = java.time.Instant.ofEpochSecond(it.entryMinute * 60L)
                .atZone(java.time.ZoneId.of(TEST_BITACORA_ZONE)).toLocalTime()
            val delta = java.time.Duration.between(parameters.centralEntryTime, localEntry).toMinutes()
            assertTrue(delta in -30..30)
        }
    }

    @Test
    fun `same inputs generate exactly the same calendar and UUIDs`() {
        assertEquals(
            TestBitacoraGenerator.generate(parameters),
            TestBitacoraGenerator.generate(parameters)
        )
    }

    @Test
    fun `different seed changes generated content`() {
        val first = TestBitacoraGenerator.generate(parameters)
        val second = TestBitacoraGenerator.generate(parameters.copy(seed = parameters.seed + 1))
        assertNotEquals(first.workdays, second.workdays)
    }

    @Test
    fun `one eligible day reports sample deviation as not verifiable`() {
        val sunday = LocalDate.of(2026, 8, 9)
        val batch = TestBitacoraGenerator.generate(
            parameters.copy(startDate = sunday, endDate = sunday, sundayPercentage = 100)
        )
        assertEquals(1, batch.workdays.size)
        assertNull(batch.sampleDeviationMinutes)
    }

    @Test
    fun `non administrator and disabled production build are blocked`() {
        expectSecurity { TestBitacoraAccessPolicy.requireAuthorized(false, true) }
        expectSecurity { TestBitacoraAccessPolicy.requireAuthorized(true, false) }
        TestBitacoraAccessPolicy.requireAuthorized(true, true)
    }

    @Test
    fun `existing real records another batch and UUID collision are blocked`() {
        val expected = setOf("a", "b")
        expectValidation {
            TestBitacoraConflictPolicy.requireNoConflict(setOf("real"), 1, 0, expected)
        }
        expectValidation {
            TestBitacoraConflictPolicy.requireNoConflict(emptySet(), 0, 1, expected)
        }
    }

    @Test
    fun `same previously inserted batch is recognized and never duplicated`() {
        try {
            TestBitacoraConflictPolicy.requireNoConflict(setOf("a", "b"), 2, 2, setOf("a", "b"))
            fail("Se esperaba rechazo del lote repetido")
        } catch (error: TestBitacoraValidationException) {
            assertEquals("El lote de prueba ya fue insertado", error.message)
        }
    }

    @Test
    fun `empty participant and inverted dates are rejected`() {
        expectIllegal { TestBitacoraGenerator.generate(parameters.copy(participantCode = "  ")) }
        expectIllegal {
            TestBitacoraGenerator.generate(
                parameters.copy(startDate = parameters.endDate, endDate = parameters.startDate)
            )
        }
    }

    private fun preview(batch: GeneratedTestBitacoraBatch) = TestBitacoraPreview(
        participant = ParticipanteOut(15, "Prueba", "Control", "P0015"),
        supervisorId = 2,
        areaId = 10,
        areaQr = "AREA|10",
        batch = batch
    )

    private fun expectSecurity(block: () -> Unit) {
        try { block(); fail("Se esperaba SecurityException") } catch (_: SecurityException) { }
    }

    private fun expectValidation(block: () -> Unit) {
        try { block(); fail("Se esperaba TestBitacoraValidationException") }
        catch (_: TestBitacoraValidationException) { }
    }

    private fun expectIllegal(block: () -> Unit) {
        try { block(); fail("Se esperaba IllegalArgumentException") }
        catch (_: IllegalArgumentException) { }
    }
}
