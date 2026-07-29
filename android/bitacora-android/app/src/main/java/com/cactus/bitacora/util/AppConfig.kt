package com.cactus.bitacora.util

import com.cactus.bitacora.BuildConfig

object AppConfig {
    const val BASE_URL = "http://161.22.47.89/bitacora/"
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 300L
    const val WRITE_TIMEOUT_SECONDS = 300L
    val FACE_TEMPLATE_AUTHORIZATION: String
        get() = BuildConfig.FACE_TEMPLATE_API_TOKEN
            .takeIf { it.isNotBlank() }
            ?.let { "Bearer $it" }
            .orEmpty()

    val ADMIN_AUTHORIZATION: String
        get() = BuildConfig.ADMIN_API_TOKEN.trim()
            .takeIf(String::isNotEmpty)
            ?.let { "Bearer $it" }
            .orEmpty()
}
