package com.cactus.bitacora.api

import retrofit2.Response
import retrofit2.http.GET

interface RootApiService {
    @GET(".")
    suspend fun root(): Response<Unit>
}
