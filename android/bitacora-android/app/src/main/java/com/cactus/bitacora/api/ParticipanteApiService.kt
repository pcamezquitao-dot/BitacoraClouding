package com.cactus.bitacora.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ParticipanteApiService {
    @GET("participante/by_qr/{qr}")
    suspend fun getParticipanteByQr(
        @Path("qr") qr: String
    ): Response<Unit>
}
