package com.cactus.bitacora.feature.testdata

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.data.local.toLocalEntity
import com.cactus.bitacora.model.ParticipanteOut
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TestBitacoraRoomTransactionTest {
    private lateinit var database: BitacoraDatabase

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BitacoraDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun `intermediate failure rolls back the complete synthetic batch`() = runBlocking {
        val entities = requests().map {
            it.toLocalEntity(syncStatus = SyncStatus.PENDIENTE_CREAR)
        }

        try {
            database.withTransaction {
                database.bitacoraDao().insertAll(entities.take(1))
                error("falla simulada")
            }
            fail("Se esperaba la falla simulada")
        } catch (_: IllegalStateException) { }

        assertEquals(0, database.bitacoraDao().countAll())
    }

    @Test
    fun `atomic rows appear in Consultar as separate entry and exit`() = runBlocking {
        val entities = requests().map {
            it.toLocalEntity(syncStatus = SyncStatus.PENDIENTE_CREAR)
        }
        database.withTransaction { database.bitacoraDao().insertAll(entities) }

        val queried = database.bitacoraDao().getMovementsForParticipants(listOf(15))
        assertEquals(2, queried.size)
        assertEquals(setOf(4, 5), queried.map { it.bitacora.tipoAnotacion }.toSet())
        assertEquals(2, queried.map { it.bitacora.clientUuid }.toSet().size)
    }

    private fun requests() = TestBitacoraPreview(
        participant = ParticipanteOut(15, "Prueba", "Control", "P0015"),
        supervisorId = 2,
        areaId = 10,
        areaQr = "AREA|10",
        batch = TestBitacoraGenerator.generate(
            TestBitacoraParameters(
                participantCode = "P0015",
                startDate = LocalDate.of(2026, 8, 9),
                endDate = LocalDate.of(2026, 8, 9),
                sundayPercentage = 100,
                seed = 1
            )
        )
    ).toCreateRequests()
}
