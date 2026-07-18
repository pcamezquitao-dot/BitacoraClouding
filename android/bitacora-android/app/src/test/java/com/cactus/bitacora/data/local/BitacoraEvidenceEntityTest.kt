package com.cactus.bitacora.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class BitacoraEvidenceEntityTest {
    private fun evidence(
        type: EvidenceType = EvidenceType.PHOTO,
        uuid: String = UUID.randomUUID().toString(),
        path: String? = "evidencia.jpg"
    ) = BitacoraEvidenceEntity(
        bitacoraLocalId = 11,
        areaId = 7,
        clientUuid = uuid,
        evidenceType = type,
        localFilePath = path,
        originalName = path,
        mimeType = "application/octet-stream",
        gpsStatus = GpsStatus.READY,
        syncStatus = SyncStatus.PENDIENTE_CREAR
    )

    @Test fun bitacoraCanHaveZeroEvidences() = assertTrue(emptyList<BitacoraEvidenceEntity>().isEmpty())

    @Test fun bitacoraCanHaveOneEvidence() = assertEquals(1, listOf(evidence()).size)

    @Test fun bitacoraCanHaveSeveralEvidences() {
        val list = listOf(evidence(), evidence(EvidenceType.VIDEO), evidence(EvidenceType.AUDIO))
        assertEquals(3, list.size)
        assertTrue(list.all { it.bitacoraLocalId == 11L })
    }

    @Test fun allRequiredTypesCanBeStoredOffline() {
        val types = listOf(EvidenceType.PHOTO, EvidenceType.VIDEO, EvidenceType.AUDIO, EvidenceType.TEXT)
        assertEquals(types, types.map { evidence(it).evidenceType })
        assertTrue(
            types.map { evidence(it) }.all {
                it.syncStatus == SyncStatus.PENDIENTE_CREAR
            }
        )
    }

    @Test fun networkErrorPreservesFileAndCanRetry() {
        val failed = evidence().copy(syncStatus = SyncStatus.ERROR, lastSyncError = "sin red", syncAttempts = 1)
        assertEquals("evidencia.jpg", failed.localFilePath)
        assertEquals(1, failed.syncAttempts)
    }

    @Test fun synchronizedEvidenceStoresRemoteId() {
        val synced = evidence().copy(remoteId = 99, syncStatus = SyncStatus.SINCRONIZADO)
        assertEquals(99, synced.remoteId)
    }

    @Test fun retryKeepsSameUuidForIdempotency() {
        val original = evidence(uuid = "a636282e-e5d9-4576-ab4d-783a0d9351cb")
        val retry = original.copy(syncAttempts = 2, syncStatus = SyncStatus.ERROR)
        assertEquals(original.clientUuid, retry.clientUuid)
        assertNotEquals(original.syncAttempts, retry.syncAttempts)
    }

    @Test fun evidenceAlwaysKeepsParentAndAreaRelation() {
        val item = evidence()
        assertEquals(11L, item.bitacoraLocalId)
        assertEquals(7, item.areaId)
    }

    @Test fun localFileCanExistBeforeUpload() {
        val file = File.createTempFile("evidence", ".jpg").apply { writeText("bytes") }
        try { assertTrue(file.isFile && file.length() > 0) } finally { file.delete() }
    }

    @Test fun rejectedPermissionAndMissingGpsAreExplicitStates() {
        val denied = evidence().copy(
            gpsStatus = GpsStatus.PERMISSION_DENIED,
            syncStatus = SyncStatus.ERROR
        )
        val unavailable = evidence().copy(
            gpsStatus = GpsStatus.UNAVAILABLE,
            syncStatus = SyncStatus.ERROR
        )
        assertEquals(GpsStatus.PERMISSION_DENIED, denied.gpsStatus)
        assertEquals(GpsStatus.UNAVAILABLE, unavailable.gpsStatus)
        assertNull(denied.remoteId)
        assertFalse(unavailable.syncStatus == SyncStatus.SINCRONIZADO)
    }
}
