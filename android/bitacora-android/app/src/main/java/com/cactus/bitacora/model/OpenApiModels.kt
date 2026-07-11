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
    val identificacion_participante: String? = null
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
    val id_bitacora: Int?,
    val id_empleado: Int,
    val id_supervisor: Int,
    val ts_in_min: Int,
    val id_tipo_evidencia: Int,
    val archivo_url: String,
    val archivo_nombre: String?,
    val archivo_hash: String?,
    val tamanio_bytes: Int?,
    val duracion_seg: Int?,
    val orden: Int?
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
