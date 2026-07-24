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
    val qr_area: String? = null
)

data class BitacoraDiariaOut(
    val id_bitacora: Int,
    val id_empleado: Int,
    val id_supervisor: Int?,
    val ts_in_min: Int,
    val ts_out_min: Int?,
    val tipo_anotacion: Int?,
    val observaciones: String?
)

data class BitacoraDiariaSyncOut(
    val id_bitacora: Int,
    val id_empleado: Int,
    val id_supervisor: Int?,
    val ts_in_min: Int,
    val ts_out_min: Int?,
    val tipo_anotacion: Int?,
    val observaciones: String?,
    val client_uuid: String?
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
    val activo: Boolean = true,
    val updated_at: String? = null
)

data class CatalogAssignmentOut(
    val id_participante: Int,
    val id_area: Int,
    val cargo: Int? = null,
    val fecha_final: String? = null,
    val activo: Boolean = true,
    val updated_at: String? = null
)

data class OfflineCatalogOut(
    val generated_at: String,
    val participantes: List<CatalogParticipantOut>,
    val areas: List<CatalogAreaOut>,
    val empleado_areas: List<CatalogAssignmentOut>
)

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
