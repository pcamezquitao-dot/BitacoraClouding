package com.cactus.bitacora.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalEvidenceRetentionPolicyTest {
    private val now = 100L * 24L * 60L * 60L * 1000L
    private val old = now - LOCAL_EVIDENCE_MIN_AGE_MILLIS - 1L

    @Test
    fun `does not remove anything while storage is within limit`() {
        assertEquals(
            emptyList<Long>(),
            selectLocalEvidenceRetentionVictims(
                candidates = listOf(candidate(1, old, 100)),
                totalLocalBytes = 100,
                now = now,
                maxBytes = 100
            )
        )
    }

    @Test
    fun `removes oldest eligible files only until storage is within limit`() {
        assertEquals(
            listOf(1L, 2L),
            selectLocalEvidenceRetentionVictims(
                candidates = listOf(
                    candidate(2, old + 1, 30),
                    candidate(1, old, 30),
                    candidate(3, old + 2, 30)
                ),
                totalLocalBytes = 150,
                now = now,
                maxBytes = 100
            )
        )
    }

    @Test
    fun `never removes pending evidence or evidence without remote copy`() {
        assertEquals(
            listOf(3L),
            selectLocalEvidenceRetentionVictims(
                candidates = listOf(
                    candidate(1, old, 60, synchronized = false),
                    candidate(2, old, 60, hasRemoteCopy = false),
                    candidate(3, old, 60)
                ),
                totalLocalBytes = 160,
                now = now,
                maxBytes = 100
            )
        )
    }

    @Test
    fun `never removes evidence newer than thirty days`() {
        assertEquals(
            emptyList<Long>(),
            selectLocalEvidenceRetentionVictims(
                candidates = listOf(
                    candidate(1, now - LOCAL_EVIDENCE_MIN_AGE_MILLIS + 1L, 100)
                ),
                totalLocalBytes = 200,
                now = now,
                maxBytes = 100
            )
        )
    }

    private fun candidate(
        id: Long,
        createdAt: Long,
        bytes: Long,
        synchronized: Boolean = true,
        hasRemoteCopy: Boolean = true
    ) = LocalEvidenceRetentionCandidate(
        localId = id,
        createdAt = createdAt,
        bytes = bytes,
        synchronized = synchronized,
        hasRemoteCopy = hasRemoteCopy
    )
}
