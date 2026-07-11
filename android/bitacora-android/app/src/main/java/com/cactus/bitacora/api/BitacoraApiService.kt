package com.cactus.bitacora.api

import com.cactus.bitacora.model.BitacoraAreaObsCreate
import com.cactus.bitacora.model.BitacoraAreaObsOut
import com.cactus.bitacora.model.BitacoraCompletaOut
import com.cactus.bitacora.model.BitacoraDiariaCreate
import com.cactus.bitacora.model.BitacoraDiariaOut
import com.cactus.bitacora.model.EvidenciaOut
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface BitacoraApiService {
    @GET("empleado-area/{id_participante}/activa")
    suspend fun obtenerAsignacionActiva(
        @Path("id_participante") idParticipante: Int
    ): EmpleadoAreaActivaOut

    @POST("bitacora_diaria")
    suspend fun crearBitacoraDiaria(
        @Body request: BitacoraDiariaCreate
    ): BitacoraDiariaOut

    @GET("bitacora_diaria/{id_bitacora}")
    suspend fun obtenerBitacoraDiaria(
        @Path("id_bitacora") idBitacora: Int
    ): BitacoraDiariaOut

    @POST("bitacora_area_observacion")
    suspend fun crearBitacoraAreaObservacion(
        @Body request: BitacoraAreaObsCreate
    ): BitacoraAreaObsOut

    @Multipart
    @POST("bitacora_area_evidencia/upload")
    suspend fun uploadEvidenciaArea(
        @Part("id_bitacora") idBitacora: RequestBody,
        @Part("id_empleado") idEmpleado: RequestBody,
        @Part("id_supervisor") idSupervisor: RequestBody,
        @Part("ts_in_min") tsInMin: RequestBody,
        @Part("id_tipo_evidencia") idTipoEvidencia: RequestBody,
        @Part("duracion_seg") duracionSeg: RequestBody?,
        @Part("orden") orden: RequestBody?,
        @Part archivo: MultipartBody.Part
    ): EvidenciaOut

    @Multipart
    @POST("bitacora_completa/upload")
    suspend fun crearBitacoraCompletaUpload(
        @Part("id_empleado") idEmpleado: RequestBody,
        @Part("id_tipo_evidencia") idTipoEvidencia: RequestBody,
        @Part archivo: MultipartBody.Part,
        @Part("id_supervisor") idSupervisor: RequestBody?,
        @Part("ts_in_min") tsInMin: RequestBody?,
        @Part("ts_out_min") tsOutMin: RequestBody?,
        @Part("tipo_anotacion") tipoAnotacion: RequestBody?,
        @Part("observaciones") observaciones: RequestBody?,
        @Part("client_uuid") clientUuid: RequestBody?,
        @Part("qr_area") qrArea: RequestBody?,
        @Part("duracion_seg") duracionSeg: RequestBody?,
        @Part("orden") orden: RequestBody?
    ): BitacoraCompletaOut
}
