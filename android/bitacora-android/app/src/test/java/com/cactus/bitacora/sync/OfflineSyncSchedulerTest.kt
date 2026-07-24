package com.cactus.bitacora.sync

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncSchedulerTest {
    @Test
    fun `worker exige red conectada y backoff`() {
        assertEquals(
            NetworkType.CONNECTED,
            OfflineSyncScheduler.connectedConstraints().requiredNetworkType
        )
        assertTrue(OfflineSyncScheduler.BACKOFF_SECONDS >= 10)
    }

    @Test
    fun `trabajos manual y periodico usan nombres estables`() {
        assertEquals(
            "bitacora-offline-sync-periodic",
            OfflineSyncScheduler.PERIODIC_WORK_NAME
        )
        assertEquals(
            "bitacora-offline-sync-manual",
            OfflineSyncScheduler.MANUAL_WORK_NAME
        )
    }

    @Test
    fun `una solicitud manual no necesita reemplazar un worker activo`() {
        assertEquals(
            androidx.work.ExistingWorkPolicy.KEEP,
            OfflineSyncScheduler.manualWorkPolicy()
        )
    }

    @Test
    fun `sincronizacion exitosa finaliza y error transitorio reintenta`() {
        assertEquals(WorkerDecision.SUCCESS, workerDecision(retryableErrors = 0))
        assertEquals(WorkerDecision.RETRY, workerDecision(retryableErrors = 1))
    }
}
