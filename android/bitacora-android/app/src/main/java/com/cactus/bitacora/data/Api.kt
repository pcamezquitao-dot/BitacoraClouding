package com.cactus.bitacora.data

import com.cactus.bitacora.data.models.AreaByQrIn
import com.cactus.bitacora.data.models.AreaOut
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import com.cactus.bitacora.data.models.HealthOut
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

object ApiConfig {
    const val BASE_URL = "http://161.22.47.89/bitacora/"
}

interface BitacoraApi {
    @GET("health")
    suspend fun health(): HealthOut

    @POST("areas/by_qr")
    suspend fun getAreaByQr(@Body request: AreaByQrIn): AreaOut

    @POST("bitacora_diaria")
    suspend fun crearBitacoraDiaria(
        @Body request: BitacoraDiariaCreate
    ): BitacoraDiariaOut

    @GET("bitacora_diaria/{id_bitacora}")
    suspend fun getBitacoraDiaria(
        @Path("id_bitacora") idBitacora: Int
    ): BitacoraDiariaOut
}

object Api {
    fun create(): BitacoraApi {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BitacoraApi::class.java)
    }
}
