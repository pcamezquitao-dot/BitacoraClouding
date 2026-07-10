package com.cactus.bitacora.repository

import com.cactus.bitacora.api.EmpleadoApiService
import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.util.NetworkResult
import com.cactus.bitacora.util.safeApiCall
import retrofit2.HttpException

class EmpleadoRepository(
    private val service: EmpleadoApiService =
        NetworkClient.createService(EmpleadoApiService::class.java)
) {
    suspend fun getSupervisorDeEmpleado(idEmpleado: Int): NetworkResult<Unit> =
        safeApiCall {
            val response = service.getSupervisorDeEmpleado(idEmpleado)
            if (!response.isSuccessful) throw HttpException(response)
            Unit
        }
}
