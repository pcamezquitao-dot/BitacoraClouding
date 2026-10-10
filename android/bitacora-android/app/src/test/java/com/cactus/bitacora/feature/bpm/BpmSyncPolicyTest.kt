package com.cactus.bitacora.feature.bpm

import com.cactus.bitacora.data.local.BpmSyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BpmSyncPolicyTest {
    @Test fun pendingIsNeverPresentedAsServerConfirmed() {
        val pending = syncLabel(BpmSyncState.LOCAL_PENDING)
        assertTrue(pending.contains("pendiente", ignoreCase = true))
        assertFalse(pending.contains("Confirmado por servidor"))
    }

    @Test fun conflictPreservesExplicitResolutionState() {
        val conflict = syncLabel(BpmSyncState.CONFLICT)
        assertTrue(conflict.contains("conflicto", ignoreCase = true))
        assertTrue(conflict.contains("conserve", ignoreCase = true))
    }

    @Test fun confirmedIsDistinctFromLocalCapture() {
        assertTrue(syncLabel(BpmSyncState.CONFIRMED).contains("servidor", ignoreCase = true))
        assertFalse(syncLabel(BpmSyncState.CONFIRMED).contains("pendiente", ignoreCase = true))
    }

    @Test fun causalQueueWaitsForUnconfirmedDependency() {
        assertTrue(dependencyDisposition(BpmSyncState.LOCAL_PENDING) == DependencyDisposition.WAIT)
        assertTrue(dependencyDisposition(BpmSyncState.SENDING) == DependencyDisposition.WAIT)
        assertTrue(dependencyDisposition(BpmSyncState.CONFIRMED) == DependencyDisposition.SEND)
    }

    @Test fun rejectedDependencyBlocksItsSuccessors() {
        assertTrue(dependencyDisposition(BpmSyncState.REJECTED) == DependencyDisposition.BLOCK)
        assertTrue(dependencyDisposition(BpmSyncState.CONFLICT) == DependencyDisposition.BLOCK)
    }
}
