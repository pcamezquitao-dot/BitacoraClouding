package com.cactus.bitacora.repository

import com.cactus.bitacora.api.HealthApiService
import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.model.HealthOut
import com.cactus.bitacora.util.NetworkResult
import com.cactus.bitacora.util.safeApiCall

class HealthRepository(
    private val service: HealthApiService =
        NetworkClient.createService(HealthApiService::class.java)
) {
    suspend fun checkHealth(): NetworkResult<HealthOut> =
        safeApiCall { service.health() }
}
