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
import retrofit2.http.Query
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import com.cactus.bitacora.model.EvidenciaOut
import com.cactus.bitacora.model.EvidenciaTextoCreate
import com.cactus.bitacora.model.FaceTemplateAuthorizedOut
import com.cactus.bitacora.model.FaceTemplateEnrollIn
import com.cactus.bitacora.model.FaceTemplateMetadataOut
import com.cactus.bitacora.model.FaceTemplateDeactivateIn
import com.cactus.bitacora.model.OfflineCatalogOut
import com.cactus.bitacora.model.BitacoraDiariaSyncOut
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.DELETE

object ApiConfig {
    val BASE_URL: String
        get() = AppConfig.BASE_URL
}

interface BitacoraApi {
    @GET("bitacora_diaria")
    suspend fun getBitacoras(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): List<BitacoraDiariaSyncOut>

    @GET("catalogos/offline")
    suspend fun getOfflineCatalogs(): OfflineCatalogOut

    @DELETE("bitacora-area-evidencias/{id_evidencia}")
    suspend fun deleteEvidence(
        @Path("id_evidencia") idEvidence: Int
    )

    @DELETE("bitacora_diaria/{id_bitacora}")
    suspend fun deleteBitacora(
        @Path("id_bitacora") idBitacora: Int
    )

    @GET("bitacora-area-evidencias")
    suspend fun getEvidences(
        @Query("id_bitacora") idBitacora: Int,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): List<EvidenciaOut>

    @POST("face-templates/enroll")
    suspend fun enrollFaceTemplate(
        @Header("Authorization") authorization: String,
        @Body payload: FaceTemplateEnrollIn
    ): FaceTemplateMetadataOut

    @GET("face-templates/authorized/active")
    suspend fun getAuthorizedFaceTemplates(
        @Header("Authorization") authorization: String
    ): List<FaceTemplateAuthorizedOut>

    @PATCH("face-templates/{id_face_template}/deactivate")
    suspend fun deactivateFaceTemplate(
        @Header("Authorization") authorization: String,
        @Path("id_face_template") idFaceTemplate: Int,
        @Body payload: FaceTemplateDeactivateIn
    )
    @GET("participante/by_qr/{qr}")
    suspend fun getParticipanteByQr(@Path("qr") qr: String): ParticipanteOut

    @GET("participante/search")
    suspend fun searchParticipantes(@Query("q") query: String): List<ParticipanteOut>

    @GET("empleado-area/{id_participante}/activa")
    suspend fun getAsignacionActiva(
        @Path("id_participante") idParticipante: Int
    ): EmpleadoAreaActivaOut

    @GET("empleado-area/{id_participante}/activas")
    suspend fun getAsignacionesActivas(
        @Path("id_participante") idParticipante: Int
    ): List<EmpleadoAreaActivaOut>

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

    @POST("bitacora-area-evidencias")
    suspend fun createTextEvidence(
        @Body request: EvidenciaTextoCreate
    ): EvidenciaOut

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
        @Part("archivo_hash") fileHash: RequestBody?,
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
