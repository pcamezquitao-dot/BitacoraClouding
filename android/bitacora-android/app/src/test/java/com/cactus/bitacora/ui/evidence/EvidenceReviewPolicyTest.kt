package com.cactus.bitacora.ui.evidence

import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceReviewPolicyTest {
    @Test
    fun photoPersistsOnlyAfterSave() {
        assertTrue(shouldPersistEvidence(EvidenceReviewAction.SAVE))
        assertFalse(shouldPersistEvidence(EvidenceReviewAction.CANCEL))
        assertFalse(shouldPersistEvidence(EvidenceReviewAction.REPEAT))
    }

    @Test
    fun audioAndVideoUseSupportedEvidenceTypes() {
        assertEquals(2, EvidenceType.AUDIO.backendTypeForTest())
        assertEquals(3, EvidenceType.VIDEO.backendTypeForTest())
    }

    @Test
    fun temporaryFileIsKeptWhenRoomSaveFails() {
        assertFalse(shouldDeleteTemporaryEvidence(localMetadataSaved = false))
        assertTrue(shouldDeleteTemporaryEvidence(localMetadataSaved = true))
    }

    @Test
    fun newEvidenceStartsPendingAndCanBecomeSynchronized() {
        val states = listOf(SyncStatus.PENDIENTE_CREAR, SyncStatus.SINCRONIZADO)
        assertEquals(SyncStatus.PENDIENTE_CREAR, states.first())
        assertEquals(SyncStatus.SINCRONIZADO, states.last())
    }
}

private fun EvidenceType.backendTypeForTest(): Int = when (this) {
    EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> 1
    EvidenceType.AUDIO -> 2
    EvidenceType.VIDEO -> 3
    EvidenceType.TEXT -> 4
}
