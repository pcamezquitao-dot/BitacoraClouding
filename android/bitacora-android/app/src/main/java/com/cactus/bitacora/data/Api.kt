package com.cactus.bitacora.data

import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.data.models.AreaByQrIn
import com.cactus.bitacora.data.models.AreaOut
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import com.cactus.bitacora.data.models.HealthOut
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.ParticipanteOut
import com.cactus.bitacora.model.SupervisorEmpleadoOut
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
import com.cactus.bitacora.model.AreaTreeNodeOut
import com.cactus.bitacora.model.CalendarHolidayUpdateIn
import com.cactus.bitacora.model.CalendarTreeNodeOut
import com.cactus.bitacora.model.AdministrativeAreaIn
import com.cactus.bitacora.model.AdministrativeAreaOut
import com.cactus.bitacora.model.EmployeeAreaAdminIn
import com.cactus.bitacora.model.EmployeeAreaAdminOut
import com.cactus.bitacora.model.EmployeeAreaAssignmentOut
import com.cactus.bitacora.model.EmployeeAreaTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaUpdateIn
import com.cactus.bitacora.model.ParticipantOptionOut
import com.cactus.bitacora.model.ObjetoMonitoreoSatelitalOut
import com.cactus.bitacora.model.ParticipantTypeAdminIn
import com.cactus.bitacora.model.ParticipantTypeAdminOut
import com.cactus.bitacora.model.ParticipantTypeStatusIn
import com.cactus.bitacora.model.ReservoirSatelliteImageOut
import com.cactus.bitacora.model.ReservoirSatelliteOut
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Url

object ApiConfig {
    val BASE_URL: String
        get() = AppConfig.BASE_URL
}

interface BitacoraApi {
    @POST("supervisor/identificar")
    suspend fun identifySupervisor(
        @Body payload: com.cactus.bitacora.model.SupervisorIdentifyIn
    ): com.cactus.bitacora.model.SupervisorSessionOut

    @GET("supervisor/{code}/participantes")
    suspend fun getSupervisedParticipants(
        @Path("code") code: String,
        @Query("search") search: String = ""
    ): List<com.cactus.bitacora.model.SupervisedParticipantOut>

    @POST("supervisor/movimientos")
    suspend fun createSupervisorMovement(
        @Body payload: com.cactus.bitacora.model.SupervisorMovementIn
    ): com.cactus.bitacora.model.SupervisorMovementOut

    @GET("supervisor/{code}/movimientos-hoy")
    suspend fun getSupervisorTodayMovements(
        @Path("code") code: String
    ): List<com.cactus.bitacora.model.SupervisorTodayMovementOut>

    @POST("control/supervisor/session")
    suspend fun createSupervisorControlSession(
        @Body payload: com.cactus.bitacora.model.ControlSupervisorSessionIn
    ): com.cactus.bitacora.model.ControlSupervisorTokenOut

    @GET("control/supervisor/me")
    suspend fun getSupervisorControl(
        @Header("Authorization") authorization: String,
        @Query("anio") year: Int,
        @Query("mes") month: Int
    ): com.cactus.bitacora.model.ControlSupervisorReportOut

    @GET("control/supervisor/me/participantes/{participantId}/bitacoras")
    suspend fun getSupervisorControlDayBitacoras(
        @Header("Authorization") authorization: String,
        @Path("participantId") participantId: Int,
        @Query("fecha") date: String
    ): List<com.cactus.bitacora.model.ControlBitacoraOut>

    @PATCH("control/supervisor/me/bitacoras/{bitacoraId}/observaciones")
    suspend fun updateSupervisorControlObservation(
        @Header("Authorization") authorization: String,
        @Path("bitacoraId") bitacoraId: Int,
        @Body payload: com.cactus.bitacora.model.ControlObservationUpdateIn
    ): com.cactus.bitacora.model.ControlObservationUpdateOut

    @GET("admin/participantes")
    suspend fun getAdminParticipants(
        @Query("search") search: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): com.cactus.bitacora.model.ParticipantAdminPage

    @GET("admin/participantes/tipos-documento")
    suspend fun getAdminDocumentTypes(): List<com.cactus.bitacora.model.DocumentTypeOut>

    @GET("admin/participantes/{participantId}")
    suspend fun getAdminParticipantDetail(
        @Path("participantId") participantId: Int
    ): com.cactus.bitacora.model.ParticipantAdminOut

    @POST("admin/participantes")
    suspend fun createAdminParticipant(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Body payload: com.cactus.bitacora.model.ParticipantAdminIn
    ): com.cactus.bitacora.model.ParticipantAdminOut

    @PUT("admin/participantes/{participantId}")
    suspend fun updateAdminParticipant(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("participantId") participantId: Int,
        @Body payload: com.cactus.bitacora.model.ParticipantAdminIn
    ): com.cactus.bitacora.model.ParticipantAdminOut

    @DELETE("admin/participantes/{participantId}")
    suspend fun retireAdminParticipant(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("participantId") participantId: Int
    ): com.cactus.bitacora.model.ParticipantAdminOut

    @GET("bitacora_diaria")
    suspend fun getBitacoras(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): List<BitacoraDiariaSyncOut>

    @GET("catalogos/offline")
    suspend fun getOfflineCatalogs(): OfflineCatalogOut

    @GET("admin/tipos-participante")
    suspend fun getAdminParticipantTypes(
        @Header("Authorization") authorization: String,
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String
    ): List<ParticipantTypeAdminOut>

    @POST("admin/tipos-participante")
    suspend fun createAdminParticipantType(
        @Header("Authorization") authorization: String,
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Body payload: ParticipantTypeAdminIn
    ): ParticipantTypeAdminOut

    @PUT("admin/tipos-participante/{codigo}")
    suspend fun updateAdminParticipantType(
        @Header("Authorization") authorization: String,
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("codigo") code: Int,
        @Body payload: ParticipantTypeAdminIn
    ): ParticipantTypeAdminOut

    @PUT("admin/tipos-participante/{codigo}/estado")
    suspend fun setAdminParticipantTypeStatus(
        @Header("Authorization") authorization: String,
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("codigo") code: Int,
        @Body payload: ParticipantTypeStatusIn
    ): ParticipantTypeAdminOut

    @GET("admin/areas/arbol")
    suspend fun getAdminAreaTree(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String
    ): Response<List<AreaTreeNodeOut>>

    @GET("admin/calendario/arbol")
    suspend fun getAdminCalendarTree(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String
    ): Response<List<CalendarTreeNodeOut>>

    @PATCH("admin/calendario/dias/{idPeriodo}/festivo")
    suspend fun updateAdminCalendarHoliday(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("idPeriodo") idPeriodo: Long,
        @Body payload: CalendarHolidayUpdateIn
    ): Response<CalendarTreeNodeOut>

    @POST("admin/areas")
    suspend fun createAdminArea(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Body payload: AdministrativeAreaIn
    ): Response<AdministrativeAreaOut>

    @PUT("admin/areas/{idArea}")
    suspend fun updateAdminArea(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("idArea") idArea: Int,
        @Body payload: AdministrativeAreaIn
    ): Response<AdministrativeAreaOut>

    @DELETE("admin/areas/{idArea}")
    suspend fun deleteAdminArea(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("idArea") idArea: Int
    ): Response<Unit>

    @POST("admin/empleado-area")
    suspend fun createAdminEmployeeArea(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Body payload: EmployeeAreaAdminIn
    ): EmployeeAreaAdminOut

    @GET("admin/empleado-area/tree")
    suspend fun getAdminEmployeeAreaTree(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String
    ): Response<List<EmployeeAreaTreeNodeOut>>

    @GET("admin/empleado-area/tipos-participante")
    suspend fun getAdminEmployeeAreaTypes(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String
    ): Response<List<ParticipantTypeAdminOut>>

    @GET("admin/participantes/options")
    suspend fun getAdminParticipantOptions(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Query("search") search: String
    ): Response<List<ParticipantOptionOut>>

    @PUT("admin/empleado-area/{idAssignment}")
    suspend fun updateAdminEmployeeArea(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("idAssignment") idAssignment: Int,
        @Body payload: EmployeeAreaUpdateIn
    ): Response<EmployeeAreaAssignmentOut>

    @DELETE("admin/empleado-area/{idAssignment}")
    suspend fun retireAdminEmployeeArea(
        @Header("X-Admin-Actor") actor: String,
        @Header("X-Admin-Device") device: String,
        @Path("idAssignment") idAssignment: Int
    ): Response<Unit>

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

    @GET("empleados/{id_empleado}/supervisor")
    suspend fun getSupervisorForEmployee(
        @Path("id_empleado") idEmpleado: Int
    ): SupervisorEmpleadoOut

    @GET("health")
    suspend fun health(): HealthOut

    @GET("satelital/objetos")
    suspend fun getObjetosMonitoreoSatelital(): List<ObjetoMonitoreoSatelitalOut>

    @GET("satelital/embalses")
    suspend fun getReservoirs(): List<ReservoirSatelliteOut>

    @GET("satelital/embalses/{id_embalse}/imagenes")
    suspend fun getReservoirImages(
        @Path("id_embalse") reservoirId: Int
    ): List<ReservoirSatelliteImageOut>

    @GET
    suspend fun downloadSatelliteImage(@Url url: String): ResponseBody

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
