package com.cactus.bitacora.location

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double?,
    val timestamp: Long,
    val provider: String
)
