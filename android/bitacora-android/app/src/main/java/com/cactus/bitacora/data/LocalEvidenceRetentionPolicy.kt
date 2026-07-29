package com.cactus.bitacora.data

internal const val LOCAL_EVIDENCE_MAX_BYTES = 2L * 1024L * 1024L * 1024L
internal const val LOCAL_EVIDENCE_MIN_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L

internal data class LocalEvidenceRetentionCandidate(
    val localId: Long,
    val createdAt: Long,
    val bytes: Long,
    val synchronized: Boolean,
    val hasRemoteCopy: Boolean
)

internal fun selectLocalEvidenceRetentionVictims(
    candidates: List<LocalEvidenceRetentionCandidate>,
    totalLocalBytes: Long,
    now: Long,
    maxBytes: Long = LOCAL_EVIDENCE_MAX_BYTES,
    minAgeMillis: Long = LOCAL_EVIDENCE_MIN_AGE_MILLIS
): List<Long> {
    if (totalLocalBytes <= maxBytes) return emptyList()

    var remainingBytes = totalLocalBytes
    val oldestAllowedCreatedAt = now - minAgeMillis
    val victims = mutableListOf<Long>()
    candidates
        .asSequence()
        .filter {
            it.synchronized &&
                it.hasRemoteCopy &&
                it.createdAt <= oldestAllowedCreatedAt
        }
        .sortedWith(compareBy<LocalEvidenceRetentionCandidate> { it.createdAt }.thenBy { it.localId })
        .forEach { candidate ->
            if (remainingBytes > maxBytes) {
                victims += candidate.localId
                remainingBytes -= candidate.bytes.coerceAtLeast(0L)
            }
        }
    return victims
}
