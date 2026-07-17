package com.cactus.bitacora.data

import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.data.models.AreaByQrIn
import com.cactus.bitacora.data.models.AreaOut
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import com.cactus.bitacora.data.models.HealthOut
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.ParticipanteOut
import com.cactus.bitacora.util.AppConfig
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import com.cactus.bitacora.model.EvidenciaOut

object ApiConfig {
    val BASE_URL: String
        get() = AppConfig.BASE_URL
}

interface BitacoraApi {
    @GET("participante/by_qr/{qr}")
    suspend fun getParticipanteByQr(@Path("qr") qr: String): ParticipanteOut

    @GET("empleado-area/{id_participante}/activa")
    suspend fun getAsignacionActiva(
        @Path("id_participante") idParticipante: Int
    ): EmpleadoAreaActivaOut

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

    @Multipart
    @POST("bitacora-area-evidencias/upload")
    suspend fun uploadEvidence(
        @Part file: MultipartBody.Part,
        @Part("id_bitacora") idBitacora: RequestBody,
        @Part("id_area") idArea: RequestBody,
        @Part("ts_in_min") tsInMin: RequestBody,
        @Part("id_tipo_evidencia") type: RequestBody,
        @Part("uuid_cliente") clientUuid: RequestBody,
        @Part("archivo_nombre") originalName: RequestBody?,
        @Part("mime_type") mimeType: RequestBody?,
        @Part("duracion_seg") duration: RequestBody?,
        @Part("tamanio_bytes") size: RequestBody?,
        @Part("orden") order: RequestBody?,
        @Part("latitud") latitude: RequestBody?,
        @Part("longitud") longitude: RequestBody?,
        @Part("precision_gps") accuracy: RequestBody?
    ): EvidenciaOut
}

object Api {
    fun create(): BitacoraApi =
        NetworkClient.createService(BitacoraApi::class.java)
}
