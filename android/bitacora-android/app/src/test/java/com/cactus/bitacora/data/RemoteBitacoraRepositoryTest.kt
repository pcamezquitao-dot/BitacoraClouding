package com.cactus.bitacora.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.BitacoraLocalEntity
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.model.BitacoraDiariaSyncOut
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteBitacoraRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "remote-bitacora-repository-test.db"
    private lateinit var database: BitacoraDatabase

    @Before fun setUp() {
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun downloadsZeroOneTwentyAndSixtyFour() = runBlocking {
        listOf(0, 1, 20, 64).forEach { count ->
            database.clearAllTables()
            val rows = (5 until 5 + count).map(::remoteBitacora)
            val result = repository(FakeBitacoraServer(rows)).refreshAndGet()
            assertEquals(count, result.size)
        }
    }

    @Test fun repeatedTimestampsAndNullObservationsDoNotReplaceRowsAndId68Appears() = runBlocking {
        val rows = (5..68).map { remoteBitacora(it, timestamp = 777, observations = null) }
        val result = repository(FakeBitacoraServer(rows)).refreshAndGet()

        assertEquals(64, result.size)
        assertEquals(64, result.mapNotNull { it.bitacora.backendId }.distinct().size)
        assertTrue(result.any { it.bitacora.backendId == 68 })
        assertTrue(result.all { it.bitacora.observaciones == null })
    }

    @Test fun downloadsAllPagesAndManualRetryDoesNotDuplicate() = runBlocking {
        val server = FakeBitacoraServer((1..201).map(::remoteBitacora))
        val repository = repository(server)

        repository.refreshAndGet()
        val second = repository.refreshAndGet()

        assertEquals(201, second.size)
        assertEquals(listOf(0, 200, 0, 200), server.requestedOffsets)
    }

    @Test fun preservesPendingLocalAndReconcilesUploadedUuid() = runBlocking {
        database.bitacoraDao().insert(localPending("local-only"))
        database.bitacoraDao().insert(localPending("uploaded-uuid"))
        val result = repository(
            FakeBitacoraServer(listOf(remoteBitacora(68, uuid = "uploaded-uuid")))
        ).refreshAndGet()

        assertEquals(2, result.size)
        assertTrue(result.any { it.bitacora.clientUuid == "local-only" && it.bitacora.backendId == null })
        val reconciled = result.single { it.bitacora.clientUuid == "uploaded-uuid" }.bitacora
        assertEquals(68, reconciled.backendId)
        assertEquals(SyncStatus.SINCRONIZADO, reconciled.syncStatus)
    }

    @Test fun networkFailureAndDatabaseReopenPreserveRows() = runBlocking {
        database.bitacoraDao().insert(localPending("offline"))
        val offline = FakeBitacoraServer().apply { online = false }
        assertEquals(1, repository(offline).refreshAndGet().size)

        repository(FakeBitacoraServer((5..68).map(::remoteBitacora))).refreshAndGet()
        database.close()
        database = openDatabase()
        val persisted = database.bitacoraDao().getAllForQuery()

        assertEquals(65, persisted.size)
        assertTrue(persisted.any { it.bitacora.backendId == 68 })
        assertTrue(persisted.any { it.bitacora.clientUuid == "offline" })
    }

    private fun repository(server: FakeBitacoraServer) =
        RemoteBitacoraRepository(server.api, database.bitacoraDao())

    private fun openDatabase() =
        Room.databaseBuilder(context, BitacoraDatabase::class.java, databaseName)
            .allowMainThreadQueries().build()
}

private class FakeBitacoraServer(initial: List<BitacoraDiariaSyncOut> = emptyList()) {
    private val rows = initial.toList()
    val requestedOffsets = mutableListOf<Int>()
    var online = true
    val api: BitacoraApi = Proxy.newProxyInstance(
        BitacoraApi::class.java.classLoader,
        arrayOf(BitacoraApi::class.java)
    ) { _, method, arguments ->
        if (method.name != "getBitacoras") throw UnsupportedOperationException(method.name)
        if (!online) throw IOException("sin red")
        val offset = arguments!![0] as Int
        val limit = arguments[1] as Int
        requestedOffsets += offset
        rows.drop(offset).take(limit)
    } as BitacoraApi
}

private fun remoteBitacora(
    id: Int,
    timestamp: Int = 1000 + id,
    observations: String? = "bitacora-$id",
    uuid: String? = "uuid-$id"
) = BitacoraDiariaSyncOut(
    id_bitacora = id,
    id_empleado = 14,
    id_supervisor = 2,
    ts_in_min = timestamp,
    ts_out_min = null,
    tipo_anotacion = null,
    observaciones = observations,
    client_uuid = uuid
)

private fun localPending(uuid: String) = BitacoraLocalEntity(
    idEmpleado = 14,
    clientUuid = uuid,
    syncStatus = SyncStatus.PENDIENTE_CREAR
)
