package com.cactus.bitacora.api

import com.cactus.bitacora.model.ParticipanteOut
import retrofit2.http.GET
import retrofit2.http.Path

interface ParticipanteApiService {
    @GET("participante/by_qr/{qr}")
    suspend fun getParticipanteByQr(
        @Path("qr") qr: String
    ): ParticipanteOut
}
