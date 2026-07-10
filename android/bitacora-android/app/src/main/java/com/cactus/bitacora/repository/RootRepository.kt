package com.cactus.bitacora.repository

import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.api.RootApiService
import com.cactus.bitacora.util.NetworkResult
import com.cactus.bitacora.util.safeApiCall
import retrofit2.HttpException

class RootRepository(
    private val service: RootApiService =
        NetworkClient.createService(RootApiService::class.java)
) {
    suspend fun root(): NetworkResult<Unit> =
        safeApiCall {
            val response = service.root()
            if (!response.isSuccessful) throw HttpException(response)
            Unit
        }
}
