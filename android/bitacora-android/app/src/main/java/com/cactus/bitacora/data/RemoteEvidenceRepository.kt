package com.cactus.bitacora.data

import android.util.Log
import com.cactus.bitacora.data.local.BitacoraEvidenceDao
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.GpsStatus
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.model.EvidenciaOut
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException

internal class RemoteEvidenceRepository(
    private val api: BitacoraApi,
    private val dao: BitacoraEvidenceDao
) {
    suspend fun refreshAndGet(localBitacoraId: Long, remoteBitacoraId: Int?): List<BitacoraEvidenceEntity> {
        if (remoteBitacoraId == null) {
            return deduplicate(dao.getForBitacora(localBitacoraId, null))
        }
        try {
            downloadAll(remoteBitacoraId).forEach { remote ->
                dao.mergeRemoteEvidence(remote.toLocal(localBitacoraId))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(
                "BitacoraQuery",
                "No se pudieron refrescar evidencias de bitacora=$remoteBitacoraId; " +
                    "se conserva SQLite: ${error.message}"
            )
        }
        return deduplicate(dao.getForBitacora(localBitacoraId, remoteBitacoraId))
    }

    private suspend fun downloadAll(remoteBitacoraId: Int): List<EvidenciaOut> {
        val result = mutableListOf<EvidenciaOut>()
        var offset = 0
        while (true) {
            val page = api.getEvidences(remoteBitacoraId, offset, PAGE_SIZE)
            result += page
            if (page.size < PAGE_SIZE) return result
            offset += page.size
        }
    }

    private fun EvidenciaOut.toLocal(localBitacoraId: Long) = BitacoraEvidenceEntity(
        remoteId = id_evidencia,
        bitacoraLocalId = localBitacoraId,
        bitacoraServerId = id_bitacora,
        areaId = id_area,
        clientUuid = uuid_cliente,
        evidenceType = id_tipo_evidencia.toEvidenceType(),
        textContent = contenido_texto,
        originalName = archivo_nombre,
        localFilePath = null,
        mimeType = mime_type,
        fileSize = tamanio_bytes,
        fileHash = archivo_hash,
        durationSeconds = duracion_seg,
        createdAt = parseServerDate(created_at),
        latitude = latitud,
        longitude = longitud,
        accuracy = precision_gps?.toFloat(),
        gpsStatus = if (latitud != null && longitud != null) GpsStatus.READY else GpsStatus.UNAVAILABLE,
        syncStatus = SyncStatus.SINCRONIZADO
    )

    private fun Int.toEvidenceType(): EvidenceType = when (this) {
        1 -> EvidenceType.PHOTO
        2 -> EvidenceType.AUDIO
        3 -> EvidenceType.VIDEO
        4 -> EvidenceType.TEXT
        else -> EvidenceType.TEXT
    }

    private fun parseServerDate(value: String): Long {
        val normalized = value.take(19)
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(normalized)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private fun deduplicate(rows: List<BitacoraEvidenceEntity>) = rows.distinctBy {
        when {
            it.clientUuid.isNotBlank() -> "uuid:${it.clientUuid}"
            it.remoteId != null -> "remote:${it.remoteId}"
            else -> "local:${it.localId}"
        }
    }

    private companion object {
        const val PAGE_SIZE = 200
    }
}
