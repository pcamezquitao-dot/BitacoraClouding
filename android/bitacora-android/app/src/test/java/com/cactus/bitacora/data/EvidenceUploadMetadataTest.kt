package com.cactus.bitacora.data

import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.SyncStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceUploadMetadataTest {
    @Test fun repairsLegacyJpegNameAndOctetStreamMime() {
        val file = temporary(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        val result = evidenceUploadMetadata(file, EvidenceType.PHOTO, "captura", "application/octet-stream")
        assertEquals("captura.jpg", result.filename)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(1, result.backendType)
    }

    @Test fun repairsLegacyAmrAudio() {
        val file = temporary("#!AMR\ncontenido".toByteArray())
        val result = evidenceUploadMetadata(file, EvidenceType.AUDIO, "grabacion.audio", null)
        assertEquals("grabacion.amr", result.filename)
        assertEquals("audio/amr", result.mimeType)
        assertEquals(2, result.backendType)
    }

    @Test fun sendsMp4WithValidVideoType() {
        val header = ByteArray(16).also { "ftyp".toByteArray().copyInto(it, 4) }
        val result = evidenceUploadMetadata(temporary(header), EvidenceType.VIDEO, "video", null)
        assertEquals("video.mp4", result.filename)
        assertEquals("video/mp4", result.mimeType)
        assertEquals(3, result.backendType)
    }

    @Test fun neverSendsTextAsIdTipoFour() {
        assertThrows(IllegalArgumentException::class.java) {
            evidenceUploadMetadata(temporary("texto".toByteArray()), EvidenceType.TEXT, "nota.txt", "text/plain")
        }
    }

    @Test fun errorDiagnosticShowsExactHistoricalTextTypeAndServerDetail() {
        val result = evidenceErrorDiagnostic(
            BitacoraEvidenceEntity(
                localId = 16,
                bitacoraLocalId = 8,
                areaId = 3,
                clientUuid = "controlled-text",
                evidenceType = EvidenceType.TEXT,
                textContent = "Observación controlada",
                originalName = "observacion.txt",
                localFilePath = "/data/user/0/com.cactus.bitacora/files/evidencias/observacion.txt",
                mimeType = "text/plain",
                fileSize = 23,
                syncStatus = SyncStatus.ERROR,
                lastSyncError = "HTTP 422 {\"detail\":\"id_tipo_evidencia invalid\"}"
            ),
            serverBitacoraId = 68
        )
        assertTrue(result.contains("ID evidencia local: 16"))
        assertTrue(result.contains("ID bitácora local: 8"))
        assertTrue(result.contains("ID bitácora servidor: 68"))
        assertTrue(result.contains("id_tipo_evidencia: 4"))
        assertTrue(result.contains("Tipo lógico: texto"))
        assertTrue(result.contains("Archivo: observacion.txt"))
        assertTrue(result.contains("Extensión: txt"))
        assertTrue(result.contains("MIME: text/plain"))
        assertTrue(result.contains("Tamaño: 23 bytes"))
        assertTrue(result.contains("HTTP 422"))
    }

    private fun temporary(bytes: ByteArray): File =
        kotlin.io.path.createTempFile("evidence-upload", ".bin").toFile().apply {
            writeBytes(bytes)
            deleteOnExit()
        }
}
