package com.cactus.bitacora.util

sealed interface NetworkResult<out T> {
    data object Loading : NetworkResult<Nothing>
    data class Success<out T>(val data: T) : NetworkResult<T>
    data class Error(
        val message: String,
        val httpCode: Int? = null
    ) : NetworkResult<Nothing>
    data class Offline(val message: String) : NetworkResult<Nothing>
}
