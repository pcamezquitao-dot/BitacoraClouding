package com.cactus.bitacora.repository

import com.cactus.bitacora.api.BitacoraApiService
import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.model.BitacoraAreaObsCreate
import com.cactus.bitacora.model.BitacoraAreaObsOut
import com.cactus.bitacora.model.BitacoraCompletaOut
import com.cactus.bitacora.model.BitacoraDiariaCreate
import com.cactus.bitacora.model.BitacoraDiariaOut
import com.cactus.bitacora.model.EvidenciaOut
import com.cactus.bitacora.util.NetworkResult
import com.cactus.bitacora.util.safeApiCall
import okhttp3.MultipartBody
import okhttp3.RequestBody

class BitacoraRepository(
    private val service: BitacoraApiService =
        NetworkClient.createService(BitacoraApiService::class.java)
) {
    suspend fun crearBitacoraDiaria(
        request: BitacoraDiariaCreate
    ): NetworkResult<BitacoraDiariaOut> =
        safeApiCall { service.crearBitacoraDiaria(request) }

    suspend fun obtenerBitacoraDiaria(
        idBitacora: Int
    ): NetworkResult<BitacoraDiariaOut> =
        safeApiCall { service.obtenerBitacoraDiaria(idBitacora) }

    suspend fun crearBitacoraAreaObservacion(
        request: BitacoraAreaObsCreate
    ): NetworkResult<BitacoraAreaObsOut> =
        safeApiCall { service.crearBitacoraAreaObservacion(request) }

    suspend fun uploadEvidenciaArea(
        idBitacora: RequestBody,
        idEmpleado: RequestBody,
        idSupervisor: RequestBody,
        tsInMin: RequestBody,
        idTipoEvidencia: RequestBody,
        duracionSeg: RequestBody?,
        orden: RequestBody?,
        archivo: MultipartBody.Part
    ): NetworkResult<EvidenciaOut> =
        safeApiCall {
            service.uploadEvidenciaArea(
                idBitacora = idBitacora,
                idEmpleado = idEmpleado,
                idSupervisor = idSupervisor,
                tsInMin = tsInMin,
                idTipoEvidencia = idTipoEvidencia,
                duracionSeg = duracionSeg,
                orden = orden,
                archivo = archivo
            )
        }

    suspend fun crearBitacoraCompletaUpload(
        idEmpleado: RequestBody,
        idTipoEvidencia: RequestBody,
        archivo: MultipartBody.Part,
        idSupervisor: RequestBody?,
        tsInMin: RequestBody?,
        tsOutMin: RequestBody?,
        tipoAnotacion: RequestBody?,
        observaciones: RequestBody?,
        clientUuid: RequestBody?,
        qrArea: RequestBody?,
        duracionSeg: RequestBody?,
        orden: RequestBody?
    ): NetworkResult<BitacoraCompletaOut> =
        safeApiCall {
            service.crearBitacoraCompletaUpload(
                idEmpleado = idEmpleado,
                idTipoEvidencia = idTipoEvidencia,
                archivo = archivo,
                idSupervisor = idSupervisor,
                tsInMin = tsInMin,
                tsOutMin = tsOutMin,
                tipoAnotacion = tipoAnotacion,
                observaciones = observaciones,
                clientUuid = clientUuid,
                qrArea = qrArea,
                duracionSeg = duracionSeg,
                orden = orden
            )
        }
}
