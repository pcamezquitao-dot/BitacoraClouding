package com.cactus.bitacora.model

data class ReservoirSatelliteOut(
    val id_embalse: Int,
    val nombre: String,
    val pais: String,
    val departamento: String? = null,
    val municipio: String? = null,
    val descripcion: String? = null,
    val fuente_geografica: String? = null,
    val ultima_fecha_procesada: String? = null,
    val estado_seguimiento: String = "SIN_IMAGENES"
)

data class ReservoirSatelliteImageOut(
    val id_imagen_satelital: Long,
    val id_embalse: Int,
    val fecha_captura: String,
    val porcentaje_nubes: Double? = null,
    val porcentaje_pixeles_validos: Double? = null,
    val fuente: String,
    val imagen_url: String,
    val mime_type: String,
    val estado: String = "DISPONIBLE"
)
