package com.cactus.bitacora.data.models

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

data class BitacoraDiariaCreate(
    val id_empleado: Int,
    val id_supervisor: Int? = null,
    val ts_in_min: Int? = null,
    val ts_out_min: Int? = null,
    val tipo_anotacion: Int? = null,
    val observaciones: String? = null,
    val client_uuid: String? = null
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