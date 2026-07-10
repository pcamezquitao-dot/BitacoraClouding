package com.cactus.bitacora.repository

import com.cactus.bitacora.api.AreaApiService
import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.model.AreaByQrIn
import com.cactus.bitacora.model.AreaOut
import com.cactus.bitacora.util.NetworkResult
import com.cactus.bitacora.util.safeApiCall

class AreaRepository(
    private val service: AreaApiService =
        NetworkClient.createService(AreaApiService::class.java)
) {
    suspend fun getAreaByQr(qr: String): NetworkResult<AreaOut> =
        safeApiCall { service.getAreaByQr(AreaByQrIn(qr)) }
}
