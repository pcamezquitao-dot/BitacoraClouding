package com.cactus.bitacora.data

import com.cactus.bitacora.data.models.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BitacoraApi {

    @POST("auth/qr")
    suspend fun loginQr(@Body req: QRLoginRequest): LoginResponse

    @GET("catalogos/tipo_novedad")
    suspend fun getTiposNovedad(): List<TipoNovedadOut>

    @GET("bitacora/supervisor/{id}/empleados")
    suspend fun getEmpleados(@Path("id") supervisorId: Int): List<EmpleadoOut>

    @POST("bitacora/")
    suspend fun crearBitacora(@Body req: BitacoraCreate): BitacoraCreateResponse
}

object Api {
    // PC en la misma red WiFi que el celular
    private const val BASE_URL = "http://192.168.0.5:8000/"

    fun create(): BitacoraApi {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BitacoraApi::class.java)
    }
}
