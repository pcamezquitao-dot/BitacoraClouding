package com.cactus.bitacora.data

import android.util.Log
import com.cactus.bitacora.data.local.BitacoraDao
import com.cactus.bitacora.data.local.BitacoraLocalEntity
import com.cactus.bitacora.data.local.BitacoraQueryHeader
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.model.BitacoraDiariaSyncOut
import kotlinx.coroutines.CancellationException

internal class RemoteBitacoraRepository(
    private val api: BitacoraApi,
    private val dao: BitacoraDao
) {
    suspend fun refreshAndGet(): List<BitacoraQueryHeader> {
        try {
            downloadAll().forEach { dao.mergeRemoteBitacora(it.toLocal()) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("BitacoraQuery", "No se pudieron refrescar bitacoras; se conserva SQLite: ${error.message}")
        }
        return dao.getAllForQuery()
    }

    private suspend fun downloadAll(): List<BitacoraDiariaSyncOut> {
        val result = mutableListOf<BitacoraDiariaSyncOut>()
        var offset = 0
        while (true) {
            val page = api.getBitacoras(offset, PAGE_SIZE)
            result += page
            if (page.size < PAGE_SIZE) return result
            offset += page.size
        }
    }

    private fun BitacoraDiariaSyncOut.toLocal() = BitacoraLocalEntity(
        backendId = id_bitacora,
        idEmpleado = id_empleado,
        idSupervisor = id_supervisor,
        tsInMin = ts_in_min,
        tsOutMin = ts_out_min,
        tipoAnotacion = tipo_anotacion,
        observaciones = observaciones,
        clientUuid = client_uuid?.takeIf { it.isNotBlank() } ?: "remote-bitacora-$id_bitacora",
        syncStatus = SyncStatus.SINCRONIZADO,
        createdAtMillis = ts_in_min * 60_000L,
        updatedAtMillis = System.currentTimeMillis()
    )

    private companion object {
        const val PAGE_SIZE = 200
    }
}
