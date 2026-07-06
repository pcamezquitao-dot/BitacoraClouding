package com.cactus.bitacora.data.models

data class QRLoginRequest(val qr: String)

data class ParticipanteOut(
    val id_participante: Int,
    val identificacion_participante: String,
    val nombre: String,
    val apellido: String
)

data class LoginResponse(
    val participante: ParticipanteOut,
    val es_supervisor: Boolean
)

data class EmpleadoOut(
    val id_participante: Int,
    val identificacion_participante: String,
    val nombre: String,
    val apellido: String
)

data class TipoNovedadOut(
    val tipo_novedad: Int,
    val descripcion_novedad: String
)

data class BitacoraCreate(
    val id_supervisor: Int,
    val id_empleado: Int,
    val tipo_novedad: Int,
    val observaciones: String
)

data class BitacoraCreateResponse(
    val id_bitacora: String
)
