package com.cactus.bitacora.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cactus.bitacora.data.local.BitacoraDatabase
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.GpsStatus
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.model.EvidenciaOut
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
class RemoteEvidenceRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "remote-evidence-repository-test.db"
    private lateinit var database: BitacoraDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun bitacoraWithoutEvidenceReturnsEmptyList() = runBlocking {
        val server = FakeEvidenceServer()
        val result = repository(server).refreshAndGet(7, 68)

        assertTrue(result.isEmpty())
        assertEquals(listOf(0), server.requestedOffsets)
    }

    @Test
    fun selectingBitacora68LoadsItsTwoEvidences() = runBlocking {
        val server = FakeEvidenceServer(listOf(remote(681), remote(682, type = 3)))

        val result = repository(server).refreshAndGet(7, 68)

        assertEquals(2, result.size)
        assertEquals(setOf(681, 682), result.mapNotNullTo(mutableSetOf()) { it.remoteId })
        assertTrue(result.all { it.bitacoraServerId == 68 })
    }

    @Test
    fun downloadsPhotoVideoAndAudioWithoutFiltering() = runBlocking {
        val server = FakeEvidenceServer(
            listOf(remote(1, type = 1), remote(2, type = 3), remote(3, type = 2))
        )

        val result = repository(server).refreshAndGet(7, 68)

        assertEquals(3, result.size)
        assertEquals(
            setOf(EvidenceType.PHOTO, EvidenceType.VIDEO, EvidenceType.AUDIO),
            result.mapTo(mutableSetOf()) { it.evidenceType }
        )
    }

    @Test
    fun downloadsEveryPageInsteadOfOnlyFirstFiftyOrTwoHundred() = runBlocking {
        val server = FakeEvidenceServer((1..201).map { remote(it) })

        val result = repository(server).refreshAndGet(7, 68)

        assertEquals(201, result.size)
        assertEquals(listOf(0, 200), server.requestedOffsets)
    }

    @Test
    fun retryDoesNotDuplicateRemoteEvidence() = runBlocking {
        val server = FakeEvidenceServer(listOf(remote(11), remote(12)))
        val repository = repository(server)

        repository.refreshAndGet(7, 68)
        val second = repository.refreshAndGet(7, 68)

        assertEquals(2, second.size)
        assertEquals(2, second.map { it.clientUuid }.distinct().size)
    }

    @Test
    fun uploadedOfflineEvidenceIsReconciledByUuidAndKeepsLocalFile() = runBlocking {
        val local = BitacoraEvidenceEntity(
            bitacoraLocalId = 7,
            areaId = 3,
            clientUuid = "uuid-31",
            evidenceType = EvidenceType.PHOTO,
            localFilePath = "C:/local/foto.jpg",
            latitude = 4.5,
            longitude = -74.1,
            gpsStatus = GpsStatus.READY,
            syncStatus = SyncStatus.PENDIENTE_CREAR
        )
        database.evidenceDao().insert(local)

        val result = repository(FakeEvidenceServer(listOf(remote(31))))
            .refreshAndGet(7, 68)

        assertEquals(1, result.size)
        assertEquals(31, result.single().remoteId)
        assertEquals("C:/local/foto.jpg", result.single().localFilePath)
        assertEquals(SyncStatus.SINCRONIZADO, result.single().syncStatus)
    }

    @Test
    fun networkFailurePreservesOfflineEvidence() = runBlocking {
        database.evidenceDao().insert(
            BitacoraEvidenceEntity(
                bitacoraLocalId = 7,
                areaId = 3,
                clientUuid = "local-only",
                evidenceType = EvidenceType.AUDIO,
                localFilePath = "C:/local/audio.3gp",
                gpsStatus = GpsStatus.READY,
                syncStatus = SyncStatus.PENDIENTE_CREAR
            )
        )
        val server = FakeEvidenceServer().apply { online = false }

        val result = repository(server).refreshAndGet(7, 68)

        assertEquals(1, result.size)
        assertEquals("local-only", result.single().clientUuid)
        assertNull(result.single().remoteId)
    }

    @Test
    fun downloadedEvidenceSurvivesDatabaseCloseAndReopen() = runBlocking {
        repository(FakeEvidenceServer(listOf(remote(41), remote(42))))
            .refreshAndGet(7, 68)
        database.close()

        database = openDatabase()
        val persisted = database.evidenceDao().getForBitacora(7)

        assertEquals(2, persisted.size)
        assertEquals(setOf(41, 42), persisted.mapNotNullTo(mutableSetOf()) { it.remoteId })
    }

    @Test
    fun serverAssociationFindsEvidenceWhenLocalIdsDiffer() = runBlocking {
        database.evidenceDao().insert(
            BitacoraEvidenceEntity(
                bitacoraLocalId = 99,
                bitacoraServerId = 68,
                areaId = 3,
                clientUuid = "legacy-server-associated",
                evidenceType = EvidenceType.PHOTO,
                localFilePath = null,
                gpsStatus = GpsStatus.READY,
                syncStatus = SyncStatus.SINCRONIZADO
            )
        )

        val result = repository(FakeEvidenceServer().apply { online = false })
            .refreshAndGet(7, 68)

        assertEquals(1, result.size)
        assertEquals("legacy-server-associated", result.single().clientUuid)
    }

    @Test
    fun pendingErrorMissingFileAndServerOnlyRowsRemainVisible() = runBlocking {
        val dao = database.evidenceDao()
        dao.insert(
            BitacoraEvidenceEntity(
                bitacoraLocalId = 7,
                areaId = 3,
                clientUuid = "pending-missing-file",
                evidenceType = EvidenceType.AUDIO,
                localFilePath = "C:/missing/audio.3gp",
                gpsStatus = GpsStatus.READY,
                syncStatus = SyncStatus.PENDIENTE_CREAR
            )
        )
        dao.insert(
            BitacoraEvidenceEntity(
                bitacoraLocalId = 7,
                areaId = 3,
                clientUuid = "error-row",
                evidenceType = EvidenceType.VIDEO,
                lastSyncError = "HTTP 500",
                gpsStatus = GpsStatus.READY,
                syncStatus = SyncStatus.ERROR
            )
        )

        val result = repository(FakeEvidenceServer(listOf(remote(77))))
            .refreshAndGet(7, 68)

        assertEquals(3, result.size)
        assertTrue(result.any { it.syncStatus == SyncStatus.PENDIENTE_CREAR })
        assertTrue(result.any { it.syncStatus == SyncStatus.ERROR })
        assertTrue(result.any { it.remoteId == 77 && it.localFilePath == null })
    }

    private fun repository(server: FakeEvidenceServer) =
        RemoteEvidenceRepository(server.api, database.evidenceDao())

    private fun openDatabase(): BitacoraDatabase =
        Room.databaseBuilder(context, BitacoraDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
}

private class FakeEvidenceServer(initial: List<EvidenciaOut> = emptyList()) {
    private val rows = initial.toList()
    val requestedOffsets = mutableListOf<Int>()
    var online = true

    val api: BitacoraApi = Proxy.newProxyInstance(
        BitacoraApi::class.java.classLoader,
        arrayOf(BitacoraApi::class.java)
    ) { _, method, arguments ->
        if (method.name != "getEvidences") throw UnsupportedOperationException(method.name)
        if (!online) throw IOException("sin red")
        val offset = arguments!![1] as Int
        val limit = arguments[2] as Int
        requestedOffsets += offset
        rows.drop(offset).take(limit)
    } as BitacoraApi
}

private fun remote(id: Int, type: Int = 1) = EvidenciaOut(
    id_evidencia = id,
    id_bitacora = 68,
    id_area = 3,
    ts_in_min = 100 + id,
    id_tipo_evidencia = type,
    archivo_url = "$type/evidencia-$id.dat",
    archivo_nombre = "evidencia-$id.dat",
    archivo_hash = id.toString().padStart(64, '0'),
    mime_type = when (type) {
        1 -> "image/jpeg"
        2 -> "audio/3gpp"
        3 -> "video/mp4"
        else -> "text/plain"
    },
    tamanio_bytes = 1024,
    duracion_seg = if (type in setOf(2, 3)) 5 else null,
    orden = id,
    latitud = 4.5,
    longitud = -74.1,
    precision_gps = 3.0,
    uuid_cliente = "uuid-$id",
    created_at = "2026-07-20T12:00:00Z"
)
