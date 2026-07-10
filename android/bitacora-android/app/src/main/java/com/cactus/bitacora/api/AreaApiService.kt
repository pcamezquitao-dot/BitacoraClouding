package com.cactus.bitacora.api

import com.cactus.bitacora.model.AreaByQrIn
import com.cactus.bitacora.model.AreaOut
import retrofit2.http.Body
import retrofit2.http.POST

interface AreaApiService {
    @POST("areas/by_qr")
    suspend fun getAreaByQr(@Body request: AreaByQrIn): AreaOut
}
