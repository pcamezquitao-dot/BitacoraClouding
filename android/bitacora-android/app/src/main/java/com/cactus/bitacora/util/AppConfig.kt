package com.cactus.bitacora.util

import com.cactus.bitacora.BuildConfig

object AppConfig {
    const val BASE_URL = "http://161.22.47.89/bitacora/"
    const val NETWORK_TIMEOUT_SECONDS = 30L
    val FACE_TEMPLATE_AUTHORIZATION: String
        get() = BuildConfig.FACE_TEMPLATE_API_TOKEN
            .takeIf { it.isNotBlank() }
            ?.let { "Bearer $it" }
            .orEmpty()
}
