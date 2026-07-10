package com.cactus.bitacora.repository

import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.api.ParticipanteApiService
import com.cactus.bitacora.util.NetworkResult
import com.cactus.bitacora.util.safeApiCall
import retrofit2.HttpException

class ParticipanteRepository(
    private val service: ParticipanteApiService =
        NetworkClient.createService(ParticipanteApiService::class.java)
) {
    suspend fun getParticipanteByQr(qr: String): NetworkResult<Unit> =
        safeApiCall {
            val response = service.getParticipanteByQr(qr)
            if (!response.isSuccessful) throw HttpException(response)
            Unit
        }
}
