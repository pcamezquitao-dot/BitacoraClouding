package com.cactus.bitacora.repository

import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.api.ParticipanteApiService
import com.cactus.bitacora.util.NetworkResult
import com.cactus.bitacora.util.safeApiCall
import com.cactus.bitacora.model.ParticipanteOut

class ParticipanteRepository(
    private val service: ParticipanteApiService =
        NetworkClient.createService(ParticipanteApiService::class.java)
) {
    suspend fun getParticipanteByQr(qr: String): NetworkResult<ParticipanteOut> =
        safeApiCall { service.getParticipanteByQr(qr) }
}
