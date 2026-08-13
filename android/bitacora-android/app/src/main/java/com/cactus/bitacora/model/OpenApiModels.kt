package com.cactus.bitacora.model

data class HealthOut(
    val status: String = "ok"
)

data class AreaByQrIn(
    val qr: String
)

data class AreaOut(
    val id_area: Int,
    val descripcion: String
)

data class ParticipanteOut(
    val id_participante: Int,
    val nombre: String? = null,
    val apellido: String? = null,
    val identificacion_participante: String? = null,
    val documento: String? = null
)

data class EmpleadoAreaActivaOut(
    val id_participante: Int,
    val id_area: Int,
    val area_descripcion: String? = null,
    val cargo: Int? = null,
    val fecha_final: String? = null
)

data class BitacoraDiariaCreate(
    val id_empleado: Int,
    val id_supervisor: Int? = null,
    val ts_in_min: Int? = null,
    val ts_out_min: Int? = null,
    val tipo_anotacion: Int? = null,
    val observaciones: String? = null,
    val client_uuid: String? = null,
    val qr_area: String? = null,
    val id_objeto_monitoreo: Int? = null,
    val origen_bitacora: String = "MANUAL",
    val tipo_seguimiento_satelital: String? = null
)

data class BitacoraDiariaOut(
    val id_bitacora: Int,
    val id_empleado: Int,
    val id_supervisor: Int?,
    val ts_in_min: Int,
    val ts_out_min: Int?,
    val tipo_anotacion: Int?,
    val observaciones: String?,
    val id_objeto_monitoreo: Int? = null,
    val origen_bitacora: String = "MANUAL",
    val tipo_seguimiento_satelital: String? = null
)

data class SupervisorEmpleadoOut(
    val id_empleado: Int,
    val id_supervisor: Int
)

data class SupervisorIdentifyIn(val codigo: String)
data class SupervisorAreaOut(val id_area: Int, val area: String)
data class SupervisorSessionOut(
    val id_supervisor: Int,
    val codigo: String,
    val nombre_completo: String,
    val estado: String,
    val areas: List<SupervisorAreaOut>
)
data class SupervisedParticipantOut(
    val id_participante: Int,
    val codigo: String,
    val nombre: String? = null,
    val apellido: String? = null,
    val id_area: Int,
    val area: String
)
data class SupervisorMovementIn(
    val codigo_supervisor: String,
    val id_participante: Int,
    val id_area: Int,
    val tipo: String,
    val timestamp_min: Int? = null,
    val client_uuid: String,
    val dispositivo: String? = null
)
data class SupervisorMovementOut(
    val id_bitacora: Int,
    val id_participante: Int,
    val id_supervisor: Int,
    val id_area: Int,
    val tipo: String,
    val timestamp_min: Int,
    val client_uuid: String
)
data class SupervisorTodayMovementOut(
    val id_bitacora: Int,
    val id_participante: Int,
    val id_supervisor: Int,
    val id_area: Int,
    val tipo: String,
    val timestamp_min: Int,
    val client_uuid: String,
    val codigo_participante: String,
    val nombre_completo: String,
    val area: String
)

data class BitacoraDiariaSyncOut(
    val id_bitacora: Int,
    val id_empleado: Int,
    val id_supervisor: Int?,
    val ts_in_min: Int,
    val ts_out_min: Int?,
    val tipo_anotacion: Int?,
    val observaciones: String?,
    val client_uuid: String?,
    val id_objeto_monitoreo: Int? = null,
    val origen_bitacora: String = "MANUAL",
    val tipo_seguimiento_satelital: String? = null
)

data class ObjetoMonitoreoSatelitalOut(
    val id_objeto_monitoreo: Int,
    val nombre: String,
    val tipo_objeto: String,
    val pais_codigo: String,
    val departamento_provincia: String? = null,
    val municipio_localidad: String? = null,
    val descripcion: String? = null,
    val latitud_centro: Double? = null,
    val longitud_centro: Double? = null
)


data class BitacoraAreaObsCreate(
    val id_empleado: Int,
    val qr_area: String,
    val observaciones: String? = null,
    val tipo_anotacion: Int? = null
)

data class BitacoraAreaObsOut(
    val id_bitacora: Int?,
    val id_empleado: Int,
    val id_supervisor: Int,
    val ts_in_min: Int,
    val id_area: Int,
    val area_descripcion: String
)

data class EvidenciaOut(
    val id_evidencia: Int,
    val id_bitacora: Int,
    val id_area: Int,
    val ts_in_min: Int,
    val id_tipo_evidencia: Int,
    val archivo_url: String?,
    val contenido_texto: String? = null,
    val archivo_nombre: String?,
    val archivo_hash: String?,
    val mime_type: String?,
    val tamanio_bytes: Long?,
    val duracion_seg: Int?,
    val orden: Int?,
    val latitud: Double?,
    val longitud: Double?,
    val precision_gps: Double?,
    val uuid_cliente: String,
    val created_at: String
)

data class EvidenciaTextoCreate(
    val id_bitacora: Int,
    val id_area: Int,
    val ts_in_min: Int,
    val id_tipo_evidencia: Int = 4,
    val contenido_texto: String,
    val uuid_cliente: String
)

data class CatalogParticipantOut(
    val id_participante: Int,
    val identificacion_participante: String,
    val nombre: String? = null,
    val apellido: String? = null,
    val documento: String? = null,
    val activo: Boolean = true,
    val updated_at: String? = null
)

data class CatalogAreaOut(
    val id_area: Int,
    val descripcion: String,
    val nombre_corto: String? = null,
    val nodo_padre: Int? = null,
    val activo: Boolean = true,
    val updated_at: String? = null
)

data class CatalogAssignmentOut(
    val id_participante: Int,
    val id_area: Int,
    val cargo: Int? = null,
    val fecha_inicia: String? = null,
    val fecha_final: String? = null,
    val activo: Boolean = true,
    val updated_at: String? = null,
    val id_empleado_area: Int = -(id_participante * 1_000_000 + id_area)
)

data class CatalogParticipantTypeOut(
    val codigo: Int,
    val descripcion: String,
    val activo: Boolean = true,
    val capacidades: List<String> = emptyList()
)

data class OfflineCatalogOut(
    val generated_at: String,
    val participantes: List<CatalogParticipantOut>,
    val areas: List<CatalogAreaOut>,
    val empleado_areas: List<CatalogAssignmentOut>,
    val tipos_participante: List<CatalogParticipantTypeOut> = emptyList(),
    val calendario: List<CatalogCalendarPeriodOut> = emptyList()
)

data class CatalogCalendarPeriodOut(
    val id_periodo: Long,
    val id_padre: Long? = null,
    val nivel: String,
    val codigo: String,
    val nombre: String,
    val fecha_inicio: String,
    val fecha_fin: String,
    val numero_dia_semana: Int? = null,
    val nombre_dia_semana: String? = null,
    val es_fin_semana: Boolean? = null,
    val es_festivo: Boolean = false,
    val nombre_festivo: String? = null,
    val activo: Boolean = true
)

data class ParticipantTypeAdminOut(
    val codigo: Int,
    val descripcion: String,
    val activo: Boolean,
    val capacidades: List<String>
)

data class ParticipantTypeAdminIn(
    val descripcion: String,
    val capacidades: List<String>
)

data class ParticipantTypeStatusIn(val activo: Boolean)

data class AreaTreeNodeOut(
    val id_area: Int,
    val descripcion: String,
    val nombre_corto: String? = null,
    val id_padre: Int? = null,
    val nivel: Int,
    val ruta: String
)

data class CalendarTreeNodeOut(
    val id_periodo: Long,
    val id_padre: Long? = null,
    val nivel: String,
    val codigo: String,
    val nombre: String,
    val fecha_inicio: String,
    val fecha_fin: String,
    val numero_dia_semana: Int? = null,
    val nombre_dia_semana: String? = null,
    val es_fin_semana: Boolean? = null,
    val es_festivo: Boolean,
    val nombre_festivo: String? = null,
    val hijos: List<CalendarTreeNodeOut> = emptyList()
)

data class CalendarHolidayUpdateIn(
    val es_festivo: Boolean,
    val nombre_festivo: String? = null
)

data class AdministrativeAreaIn(
    val descripcion: String,
    val nombre_corto: String? = null,
    val nodo_padre: Int? = null
)

data class AdministrativeAreaOut(
    val id_area_administrativa: Int,
    val descripcion: String,
    val nombre_corto: String? = null,
    val nodo_padre: Int? = null
)

data class EmployeeAreaAdminIn(
    val id_participante: Int,
    val id_area: Int,
    val codigo_tipo: Int,
    val descripcion: String? = null,
    val fecha_inicia: String,
    val fecha_final: String? = null
)

data class EmployeeAreaAdminOut(
    val id_empleado_area: Int,
    val id_participante: Int,
    val id_area: Int,
    val codigo_tipo: Int,
    val descripcion: String? = null,
    val fecha_inicia: String,
    val fecha_final: String? = null
)

data class EmployeeAreaAssignmentOut(
    val id_empleado_area: Int,
    val id_participante: Int,
    val codigo_participante: String,
    val nombre_completo: String,
    val codigo_tipo: Int? = null,
    val cargo: String? = null,
    val descripcion: String? = null,
    val fecha_inicia: String,
    val fecha_final: String? = null
)

data class EmployeeAreaTreeNodeOut(
    val id_area: Int,
    val descripcion: String,
    val nombre_corto: String? = null,
    val nodo_padre: Int? = null,
    val nivel: Int,
    val ruta: String,
    val cantidad_participantes: Int,
    val participantes: List<EmployeeAreaAssignmentOut> = emptyList(),
    val hijos: List<EmployeeAreaTreeNodeOut> = emptyList()
)

data class EmployeeAreaUpdateIn(
    val id_area: Int,
    val codigo_tipo: Int,
    val descripcion: String? = null,
    val fecha_inicia: String,
    val fecha_final: String? = null
)

data class ParticipantOptionOut(
    val id_participante: Int,
    val codigo: String,
    val nombres: String,
    val apellidos: String,
    val nombre_completo: String,
    val documento: String? = null
)

data class ParticipantAdminIn(
    val tipo_documento: Int,
    val documento: String,
    val identificacion_participante: String,
    val nombre: String,
    val apellido: String? = null,
    val fecha_nacimiento: String? = null,
    val sexo: String? = null,
    val fecha_entrada: String? = null,
    val fecha_salida: String? = null,
    val observaciones: String? = null,
    val email: String? = null
)

data class ParticipantAdminOut(
    val id_participante: Int,
    val tipo_documento: Int,
    val documento: String,
    val identificacion_participante: String,
    val nombre: String,
    val apellido: String? = null,
    val fecha_nacimiento: String? = null,
    val sexo: String? = null,
    val fecha_entrada: String? = null,
    val fecha_salida: String? = null,
    val observaciones: String? = null,
    val email: String? = null,
    val activo: Boolean
)

data class ParticipantAdminPage(
    val items: List<ParticipantAdminOut>,
    val total: Int,
    val offset: Int,
    val limit: Int
)

data class DocumentTypeOut(val codigo: Int, val descripcion: String)

data class FaceTemplateEnrollIn(
    val client_uuid: String,
    val id_participante: Int,
    val participant_code: String,
    val display_name: String,
    val embedding_base64: String,
    val embedding_sha256: String,
    val model_version: String,
    val encryption_version: String = "server-aesgcm-v1",
    val created_by: String? = null,
    val device_id: String? = null
)

data class FaceTemplateMetadataOut(
    val id_face_template: Int,
    val id_participante: Int,
    val participant_code: String,
    val display_name: String,
    val embedding_sha256: String,
    val model_version: String,
    val encryption_version: String,
    val enrolled_at: String,
    val active: Boolean,
    val device_id: String? = null
)

data class FaceTemplateAuthorizedOut(
    val id_face_template: Int,
    val id_participante: Int,
    val participant_code: String,
    val display_name: String,
    val embedding_sha256: String,
    val model_version: String,
    val encryption_version: String,
    val enrolled_at: String,
    val active: Boolean,
    val device_id: String? = null,
    val embedding_base64: String
)

data class FaceTemplateDeactivateIn(
    val revoked_by: String? = null,
    val revocation_reason: String? = null
)

data class BitacoraCompletaOut(
    val id_bitacora: Int,
    val evidencia: EvidenciaOut
)

data class HTTPValidationError(
    val detail: List<ValidationError>? = null
)

data class ValidationError(
    val loc: List<Any>,
    val msg: String,
    val type: String,
    val input: Any? = null,
    val ctx: Map<String, Any>? = null
)
