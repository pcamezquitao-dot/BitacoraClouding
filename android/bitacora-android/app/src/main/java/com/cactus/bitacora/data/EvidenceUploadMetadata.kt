package com.cactus.bitacora.data

import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import java.io.File

internal data class EvidenceUploadMetadata(
    val filename: String,
    val mimeType: String,
    val backendType: Int
)

internal fun evidenceBackendTypeForDiagnostic(type: EvidenceType): Int = when (type) {
    EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> 1
    EvidenceType.AUDIO -> 2
    EvidenceType.VIDEO -> 3
    EvidenceType.TEXT -> 4
}

private fun EvidenceType.logicalDiagnosticName(): String = when (this) {
    EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> "foto"
    EvidenceType.AUDIO -> "audio"
    EvidenceType.VIDEO -> "video"
    EvidenceType.TEXT -> "texto"
}

internal fun evidenceErrorDiagnostic(
    evidence: BitacoraEvidenceEntity,
    serverBitacoraId: Int?
): String {
    val file = evidence.localFilePath?.let(::File)
    val name = evidence.originalName ?: file?.name ?: "sin archivo"
    val extension = name.substringAfterLast('.', "sin extensión")
    val size = evidence.fileSize ?: file?.takeIf { it.isFile }?.length()
    return listOf(
        "ID evidencia local: ${evidence.localId}",
        "ID bitácora local: ${evidence.bitacoraLocalId}",
        "ID bitácora servidor: ${serverBitacoraId ?: "pendiente"}",
        "id_tipo_evidencia: ${evidenceBackendTypeForDiagnostic(evidence.evidenceType)}",
        "Tipo lógico: ${evidence.evidenceType.logicalDiagnosticName()}",
        "Archivo: $name",
        "Extensión: $extension",
        "MIME: ${evidence.mimeType ?: "desconocido"}",
        "Ruta local: ${evidence.localFilePath ?: "sin ruta"}",
        "Tamaño: ${size?.let { "$it bytes" } ?: "desconocido"}",
        "Servidor: ${evidence.lastSyncError ?: "sin detalle"}"
    ).joinToString("\n")
}

internal fun evidenceUploadMetadata(
    file: File,
    evidenceType: EvidenceType,
    storedName: String?,
    storedMime: String?
): EvidenceUploadMetadata {
    require(evidenceType in setOf(EvidenceType.PHOTO, EvidenceType.ID_PHOTO, EvidenceType.AUDIO, EvidenceType.VIDEO)) {
        "id_tipo_evidencia inválido: solo se permiten 1=FOTO, 2=AUDIO y 3=VIDEO"
    }
    val header = file.inputStream().use { input ->
        val buffer = ByteArray(32)
        buffer.copyOf(input.read(buffer).coerceAtLeast(0))
    }
    val normalizedMime = storedMime?.substringBefore(';')?.trim()?.lowercase()
    val storedExtension = storedName.orEmpty().substringAfterLast('.', "").lowercase()
    val detected = when {
        header.size >= 3 && header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "jpg" to "image/jpeg"
        header.size >= 8 && header.copyOfRange(0, 8)
            .contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) ->
            "png" to "image/png"
        header.decodeToString().startsWith("#!AMR\n") -> "amr" to "audio/amr"
        header.size >= 12 && header.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            header.copyOfRange(8, 12).decodeToString() == "WAVE" -> "wav" to "audio/wav"
        header.size >= 3 && header.copyOfRange(0, 3).decodeToString() == "ID3" -> "mp3" to "audio/mpeg"
        header.size >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0 ->
            "mp3" to "audio/mpeg"
        header.size >= 12 && header.copyOfRange(4, 8).decodeToString() == "ftyp" -> when {
            evidenceType == EvidenceType.VIDEO -> "mp4" to "video/mp4"
            normalizedMime == "audio/3gpp" || storedExtension == "3gp" -> "3gp" to "audio/3gpp"
            else -> "m4a" to "audio/mp4"
        }
        else -> throw IllegalArgumentException(
            "No se pudo determinar el MIME real de ${storedName ?: file.name}"
        )
    }
    val expectedBackendType = when (evidenceType) {
        EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> 1
        EvidenceType.AUDIO -> 2
        EvidenceType.VIDEO -> 3
        EvidenceType.TEXT -> error("Tipo textual no permitido")
    }
    require(
        (expectedBackendType == 1 && detected.second.startsWith("image/")) ||
            (expectedBackendType == 2 && detected.second.startsWith("audio/")) ||
            (expectedBackendType == 3 && detected.second == "video/mp4")
    ) { "El contenido del archivo no corresponde al tipo de evidencia" }
    val base = (storedName ?: file.name).substringBeforeLast('.').ifBlank { "evidencia" }
    return EvidenceUploadMetadata("$base.${detected.first}", detected.second, expectedBackendType)
}
