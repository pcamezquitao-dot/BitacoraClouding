package com.cactus.bitacora.ui.query

import com.cactus.bitacora.BuildConfig
import com.cactus.bitacora.data.AudioTranscriptionState
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.BitacoraLocalEntity
import com.cactus.bitacora.data.local.BitacoraQueryHeader
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.model.EvidenceTranscriptionOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitacoraQueryPolicyTest {
    private fun transcription(
        state: String,
        text: String? = null,
        error: String? = null,
        automatic: String? = null,
        corrected: String? = null
    ) = AudioTranscriptionState.Available(
        EvidenceTranscriptionOut(
            id_transcripcion = 1,
            id_evidencia = 50,
            id_bitacora = 68,
            estado = state,
            texto_transcrito = text,
            texto_automatico = automatic,
            texto_corregido = corrected,
            numero_reintentos = if (error == null) 0 else 1,
            ultimo_error = error,
            creado_en = "2026-09-25T12:00:00",
            actualizado_en = "2026-09-25T12:00:01"
        )
    )

    @Test
    fun audioTranscriptionStatesRemainVisibleWithoutReplacingAudio() {
        assertEquals(
            "Transcripción: pendiente de sincronizar el audio",
            transcriptionStatusLabel(AudioTranscriptionState.PendingSync)
        )
        assertEquals("Transcripción: pendiente", transcriptionStatusLabel(transcription("PENDIENTE")))
        assertEquals("Transcripción: procesando", transcriptionStatusLabel(transcription("PROCESANDO")))
        assertEquals(
            "Transcripción: error · audio ilegible",
            transcriptionStatusLabel(transcription("ERROR", error = "audio ilegible"))
        )
    }

    @Test
    fun completedTranscriptionShowsTextOnlyForCompletedState() {
        assertEquals("Texto reconocido", transcriptionText(transcription("COMPLETADA", " Texto reconocido ")))
        assertEquals(null, transcriptionText(transcription("PROCESANDO", "Texto parcial")))
        assertEquals(null, transcriptionText(AudioTranscriptionState.NotFound))
    }

    @Test
    fun correctedTranscriptionTakesPriorityAndIdentifiesItsSource() {
        val corrected = transcription(
            state = "COMPLETADA",
            text = "Texto compatible",
            automatic = "Texto automático",
            corrected = "Texto corregido"
        )
        assertEquals("Texto corregido", transcriptionText(corrected))
        assertEquals("Texto corregido", transcriptionTextLabel(corrected))

        val automatic = transcription(
            state = "COMPLETADA",
            text = "Texto compatible",
            automatic = "Texto automático"
        )
        assertEquals("Texto automático", transcriptionText(automatic))
        assertEquals("Texto automático", transcriptionTextLabel(automatic))
    }
    fun deletionActionsAreOnlyVisibleForAdministratorPermission() {
        assertTrue(canShowDeletionActions(allowDelete = true))
        assertFalse(canShowDeletionActions(allowDelete = false))
    }

    private fun evidence(
        localId: Long,
        bitacoraLocalId: Long = 7,
        bitacoraServerId: Int? = null,
        type: EvidenceType = EvidenceType.PHOTO,
        remoteId: Int? = null,
        path: String? = "evidencia.jpg",
        status: SyncStatus = SyncStatus.PENDIENTE_CREAR
    ) = BitacoraEvidenceEntity(
        localId = localId,
        remoteId = remoteId,
        bitacoraLocalId = bitacoraLocalId,
        bitacoraServerId = bitacoraServerId,
        areaId = 1,
        clientUuid = "uuid-$localId",
        evidenceType = type,
        localFilePath = path,
        syncStatus = status
    )

    @Test
    fun headerCarriesEvidenceCountWithoutLoadingFiles() {
        val header = BitacoraQueryHeader(
            bitacora = BitacoraLocalEntity(
                localId = 7,
                idEmpleado = 2,
                clientUuid = "bitacora-7",
                syncStatus = SyncStatus.SINCRONIZADO
            ),
            evidenceCount = 3
        )
        assertEquals(7L, header.bitacora.localId)
        assertEquals(3, header.evidenceCount)
    }

    @Test
    fun selectingBitacoraShowsOnlyAssociatedEvidences() {
        val result = associatedEvidences(
            7,
            68,
            listOf(evidence(1, 7), evidence(2, 8), evidence(3, 7))
        )
        assertEquals(listOf(1L, 3L), result.map { it.localId })
    }

    @Test
    fun bitacoraWithoutEvidencesReturnsEmptySection() {
        assertTrue(associatedEvidences(99, null, listOf(evidence(1, 7))).isEmpty())
    }

    @Test
    fun photoVideoAndAudioOpenInTheirViewer() {
        assertEquals(EvidenceViewerKind.PHOTO, viewerKind(EvidenceType.PHOTO))
        assertEquals(EvidenceViewerKind.VIDEO, viewerKind(EvidenceType.VIDEO))
        assertEquals(EvidenceViewerKind.AUDIO, viewerKind(EvidenceType.AUDIO))
        assertTrue(viewerUsesBoundedMedia(EvidenceViewerKind.PHOTO))
        assertTrue(viewerUsesBoundedMedia(EvidenceViewerKind.VIDEO))
        assertFalse(viewerUsesBoundedMedia(EvidenceViewerKind.AUDIO))
        assertFalse(viewerUsesBoundedMedia(EvidenceViewerKind.TEXT))
    }

    @Test
    fun videoTranscriptionUsesSameVisibleStatesAsAudio() {
        val video = evidence(9, type = EvidenceType.VIDEO, remoteId = 99)
        assertEquals(EvidenceViewerKind.VIDEO, viewerKind(video.evidenceType))
        assertEquals(
            "Texto corregido",
            transcriptionTextLabel(
                transcription(
                    state = "COMPLETADA",
                    automatic = "Texto automático",
                    corrected = "Texto corregido"
                )
            )
        )
    }

    @Test
    fun remoteEvidenceWithoutLocalFileRequiresConnection() {
        val item = evidence(1, remoteId = 44, path = null, status = SyncStatus.SINCRONIZADO)
        assertEquals("Disponible en servidor", evidenceStatusLabel(item, localFileExists = false))
        assertFalse(canOpenEvidenceLocally(item.localFilePath, fileExists = false))
        assertEquals(
            "${BuildConfig.API_BASE_URL}bitacora-area-evidencias/44/archivo",
            evidenceRemoteUrl(requireNotNull(item.remoteId))
        )
    }

    @Test
    fun missingLocalFileDoesNotAttemptToOpen() {
        assertFalse(canOpenEvidenceLocally("archivo-inexistente.jpg", fileExists = false))
        assertTrue(canOpenEvidenceLocally("archivo.jpg", fileExists = true))
    }

    @Test
    fun androidBackReturnsViewerDetailAndListInOrder() {
        assertEquals(QueryLevel.DETAIL, previousQueryLevel(QueryLevel.VIEWER))
        assertEquals(QueryLevel.LIST, previousQueryLevel(QueryLevel.DETAIL))
        assertEquals(null, previousQueryLevel(QueryLevel.LIST))
    }

    @Test
    fun queryDoesNotModifySynchronizationState() {
        val item = evidence(1, status = SyncStatus.PENDIENTE_ACTUALIZAR)
        evidenceStatusLabel(item, localFileExists = true)
        associatedEvidences(7, null, listOf(item))
        assertEquals(SyncStatus.PENDIENTE_ACTUALIZAR, item.syncStatus)
    }

    @Test
    fun associatesByLocalOrServerIdWithoutDuplicating() {
        val shared = evidence(1, bitacoraLocalId = 7, bitacoraServerId = 68)
        val serverOnly = evidence(2, bitacoraLocalId = 99, bitacoraServerId = 68)
        val unrelated = evidence(3, bitacoraLocalId = 99, bitacoraServerId = 69)

        val result = associatedEvidences(7, 68, listOf(shared, serverOnly, shared, unrelated))

        assertEquals(listOf(1L, 2L), result.map { it.localId })
        assertEquals(result.size, result.map { it.clientUuid }.distinct().size)
    }

    @Test
    fun logicallyDeletedEvidenceIsTheOnlyRowExcluded() {
        val deleted = evidence(4, status = SyncStatus.PENDIENTE_ELIMINAR)
        val error = evidence(5, status = SyncStatus.ERROR)

        val result = associatedEvidences(7, null, listOf(deleted, error))

        assertEquals(listOf(5L), result.map { it.localId })
    }

    @Test
    fun errorStateIsVisibleWithoutOpeningFile() {
        val item = evidence(1, status = SyncStatus.ERROR)
        assertEquals("Error de sincronización", evidenceStatusLabel(item, localFileExists = false))
    }
}
