package com.cactus.bitacora.ui.navigation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.BitacoraLocalEntity
import com.cactus.bitacora.data.local.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovementQueryDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "c21-movement-query.db"
    private lateinit var database: BitacoraDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, BitacoraDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun filtersParticipantsAndKeepsEntriesAndExitsSeparateInTimeOrder() = runBlocking {
        val dao = database.bitacoraDao()
        dao.insert(movement(15, 4, 100, "p15-entry"))
        dao.insert(movement(15, 5, 200, "p15-exit"))
        dao.insert(movement(16, 4, 300, "p16-entry"))
        dao.insert(movement(15, 1, 400, "p15-permission"))

        val p15 = dao.getMovementsForParticipants(listOf(15))
        assertEquals(listOf(5, 4), p15.map { it.bitacora.tipoAnotacion })
        assertEquals(listOf(200, 100), p15.map { it.bitacora.tsInMin })

        val authorized = dao.getMovementsForParticipants(listOf(15, 16))
        assertEquals(listOf(16, 15, 15), authorized.map { it.bitacora.idEmpleado })
        assertEquals(listOf(4, 5, 4), authorized.map { it.bitacora.tipoAnotacion })
    }

    private fun movement(employee: Int, type: Int, minute: Int, uuid: String) =
        BitacoraLocalEntity(
            idEmpleado = employee,
            tsInMin = minute,
            tipoAnotacion = type,
            clientUuid = uuid,
            syncStatus = SyncStatus.SINCRONIZADO,
            createdAtMillis = minute.toLong()
        )
}
