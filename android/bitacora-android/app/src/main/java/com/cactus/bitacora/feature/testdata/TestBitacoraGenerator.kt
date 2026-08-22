package com.cactus.bitacora.feature.testdata

import com.cactus.bitacora.model.ParticipanteOut
import com.cactus.bitacora.model.BitacoraDiariaCreate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val TEST_BITACORA_ORIGIN = "PRUEBA"
const val TEST_BITACORA_ZONE = "America/Bogota"

data class TestBitacoraParameters(
    val participantCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val meanMinutes: Int = 8 * 60,
    val sampleDeviationMinutes: Int = 60,
    val sundayPercentage: Int = 25,
    val centralEntryTime: LocalTime = LocalTime.of(7, 0),
    val maximumEntryVariationMinutes: Int = 30,
    val minimumDurationMinutes: Int = 5 * 60,
    val maximumDurationMinutes: Int = 11 * 60,
    val seed: Long = automaticTestBitacoraSeed(participantCode, startDate, endDate)
)

data class TestWorkday(
    val date: LocalDate,
    val entryMinute: Int,
    val exitMinute: Int,
    val durationMinutes: Int,
    val entryClientUuid: String,
    val exitClientUuid: String
) {
    val isSunday: Boolean get() = date.dayOfWeek == DayOfWeek.SUNDAY
}

data class GeneratedTestBitacoraBatch(
    val parameters: TestBitacoraParameters,
    val workdays: List<TestWorkday>,
    val meanMinutes: Double,
    val sampleDeviationMinutes: Double?
) {
    val entryCount: Int get() = workdays.size
    val exitCount: Int get() = workdays.size
    val physicalRecordCount: Int get() = workdays.size * 2
    val sundayCount: Int get() = workdays.count(TestWorkday::isSunday)
    val clientUuids: Set<String>
        get() = workdays.flatMap { listOf(it.entryClientUuid, it.exitClientUuid) }.toSet()
}

data class TestBitacoraPreview(
    val participant: ParticipanteOut,
    val supervisorId: Int,
    val areaId: Int,
    val areaQr: String,
    val batch: GeneratedTestBitacoraBatch
)

data class TestBitacoraInsertResult(
    val insertedRecords: Int,
    val entryCount: Int,
    val exitCount: Int
)

class TestBitacoraValidationException(message: String) : IllegalStateException(message)

object TestBitacoraAccessPolicy {
    fun requireAuthorized(isAdministrator: Boolean, enabledForBuild: Boolean) {
        if (!enabledForBuild) {
            throw SecurityException("La inserción de bitácoras de prueba está deshabilitada en esta compilación")
        }
        if (!isAdministrator) {
            throw SecurityException("Esta función está disponible únicamente para el administrador")
        }
    }
}

object TestBitacoraConflictPolicy {
    fun requireNoConflict(
        periodRecordUuids: Set<String>,
        periodRecordCount: Int,
        uuidCollisionCount: Int,
        expectedUuids: Set<String>
    ) {
        if (periodRecordCount == expectedUuids.size && periodRecordUuids == expectedUuids) {
            throw TestBitacoraValidationException("El lote de prueba ya fue insertado")
        }
        if (uuidCollisionCount > 0) {
            throw TestBitacoraValidationException(
                "Existe una colisión de client_uuid; no se insertó ningún registro"
            )
        }
        if (periodRecordCount > 0) {
            throw TestBitacoraValidationException(
                "El participante ya tiene registros reales o de otro lote dentro del periodo"
            )
        }
    }
}

fun TestBitacoraPreview.toCreateRequests(): List<BitacoraDiariaCreate> {
    val observation = "DATOS_PRUEBA_${batch.parameters.participantCode}"
    return batch.workdays.flatMap { day ->
        listOf(
            BitacoraDiariaCreate(
                id_empleado = participant.id_participante,
                id_supervisor = supervisorId,
                ts_in_min = day.entryMinute,
                ts_out_min = null,
                tipo_anotacion = 4,
                observaciones = observation,
                client_uuid = day.entryClientUuid,
                qr_area = areaQr,
                origen_bitacora = TEST_BITACORA_ORIGIN
            ),
            BitacoraDiariaCreate(
                id_empleado = participant.id_participante,
                id_supervisor = supervisorId,
                ts_in_min = day.exitMinute,
                ts_out_min = day.exitMinute,
                tipo_anotacion = 5,
                observaciones = observation,
                client_uuid = day.exitClientUuid,
                qr_area = areaQr,
                origen_bitacora = TEST_BITACORA_ORIGIN
            )
        )
    }
}

object TestBitacoraGenerator {
    fun generate(raw: TestBitacoraParameters): GeneratedTestBitacoraBatch {
        val parameters = raw.normalized()
        val dates = selectedDates(parameters)
        require(dates.isNotEmpty()) { "El periodo no contiene días aptos para generar marcaciones" }
        val random = Random(parameters.seed)
        val durations = boundedNormalDurations(parameters, dates.size, random)
        val zone = ZoneId.of(TEST_BITACORA_ZONE)
        val workdays = dates.mapIndexed { index, date ->
            val variation = if (parameters.maximumEntryVariationMinutes == 0) 0 else {
                random.nextInt(parameters.maximumEntryVariationMinutes * 2 + 1) -
                    parameters.maximumEntryVariationMinutes
            }
            val entry = date.atTime(parameters.centralEntryTime)
                .plusMinutes(variation.toLong())
                .atZone(zone)
                .toEpochSecond()
                .div(60)
                .toInt()
            val duration = durations[index]
            TestWorkday(
                date = date,
                entryMinute = entry,
                exitMinute = entry + duration,
                durationMinutes = duration,
                entryClientUuid = deterministicUuid(parameters, date, "ENTRADA"),
                exitClientUuid = deterministicUuid(parameters, date, "SALIDA")
            )
        }
        val mean = workdays.map { it.durationMinutes }.average()
        val deviation = workdays.map { it.durationMinutes }.sampleDeviation()
        return GeneratedTestBitacoraBatch(parameters, workdays, mean, deviation)
    }

    private fun TestBitacoraParameters.normalized(): TestBitacoraParameters {
        val code = participantCode.trim().uppercase()
        require(code.isNotBlank()) { "El código del participante es obligatorio" }
        require(!endDate.isBefore(startDate)) { "La fecha final no puede ser anterior a la inicial" }
        require(meanMinutes > 0) { "El promedio diario debe ser mayor que cero" }
        require(sampleDeviationMinutes >= 0) { "La desviación estándar no puede ser negativa" }
        require(sundayPercentage in 0..100) { "El porcentaje de domingos debe estar entre 0 y 100" }
        require(maximumEntryVariationMinutes >= 0) { "La variación de entrada no puede ser negativa" }
        require(minimumDurationMinutes > 0 && maximumDurationMinutes >= minimumDurationMinutes) {
            "Los límites de duración no son válidos"
        }
        require(meanMinutes in minimumDurationMinutes..maximumDurationMinutes) {
            "El promedio debe estar entre la duración mínima y máxima"
        }
        return copy(participantCode = code)
    }

    private fun selectedDates(parameters: TestBitacoraParameters): List<LocalDate> {
        val allDates = generateSequence(parameters.startDate) { current ->
            current.plusDays(1).takeUnless { it.isAfter(parameters.endDate) }
        }.toList()
        val ordinary = allDates.filter { it.dayOfWeek != DayOfWeek.SUNDAY }
        val sundays = allDates.filter { it.dayOfWeek == DayOfWeek.SUNDAY }
        if (sundays.isEmpty() || parameters.sundayPercentage == 0) return ordinary
        val count = ceil(sundays.size * parameters.sundayPercentage / 100.0)
            .roundToInt()
            .coerceAtLeast(1)
            .coerceAtMost(sundays.size)
        val selectedSundays = sundays.toMutableList().also {
            java.util.Collections.shuffle(it, Random(parameters.seed xor SUNDAY_SEED_MASK))
        }.take(count)
        return (ordinary + selectedSundays).sorted()
    }

    private fun boundedNormalDurations(
        parameters: TestBitacoraParameters,
        count: Int,
        random: Random
    ): List<Int> {
        if (count == 1) return listOf(
            parameters.meanMinutes.coerceIn(
                parameters.minimumDurationMinutes,
                parameters.maximumDurationMinutes
            )
        )
        var values = List(count) { random.nextGaussian() }
        repeat(5) {
            val average = values.average()
            val deviation = values.sampleDeviation() ?: 1.0
            values = values.map { value ->
                (parameters.meanMinutes + (value - average) / deviation * parameters.sampleDeviationMinutes)
                    .coerceIn(
                        parameters.minimumDurationMinutes.toDouble(),
                        parameters.maximumDurationMinutes.toDouble()
                    )
            }
        }
        val rounded = values.map(Double::roundToInt).toMutableList()
        var remaining = parameters.meanMinutes * count - rounded.sum()
        var cursor = 0
        while (remaining != 0 && cursor < count * 20) {
            val index = cursor % count
            val step = if (remaining > 0) 1 else -1
            val candidate = rounded[index] + step
            if (candidate in parameters.minimumDurationMinutes..parameters.maximumDurationMinutes) {
                rounded[index] = candidate
                remaining -= step
            }
            cursor++
        }
        return rounded
    }

    private fun deterministicUuid(
        parameters: TestBitacoraParameters,
        date: LocalDate,
        kind: String
    ): String = UUID.nameUUIDFromBytes(
        listOf(
            "BITACORA_PRUEBA",
            parameters.participantCode,
            parameters.startDate,
            parameters.endDate,
            parameters.seed,
            date,
            kind
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8)
    ).toString()

    private const val SUNDAY_SEED_MASK = 0x43_32_32_53L
}

fun automaticTestBitacoraSeed(
    participantCode: String,
    startDate: LocalDate,
    endDate: LocalDate
): Long {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("${participantCode.trim().uppercase()}|$startDate|$endDate".toByteArray())
    return bytes.take(8).fold(0L) { result, byte -> (result shl 8) or (byte.toLong() and 0xff) }
}

private fun List<out Number>.sampleDeviation(): Double? {
    if (size < 2) return null
    val mean = sumOf { it.toDouble() } / size
    return sqrt(sumOf { value ->
        val difference = value.toDouble() - mean
        difference * difference
    } / (size - 1))
}
