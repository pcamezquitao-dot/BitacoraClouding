package com.cactus.bitacora.api

import com.cactus.bitacora.model.HealthOut
import retrofit2.http.GET

interface HealthApiService {
    @GET("health")
    suspend fun health(): HealthOut
}
