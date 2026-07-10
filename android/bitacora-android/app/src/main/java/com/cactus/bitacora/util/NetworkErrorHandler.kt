package com.cactus.bitacora.util

import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

suspend fun <T> safeApiCall(call: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(call())
    } catch (e: SocketTimeoutException) {
        NetworkResult.Error("Tiempo de espera agotado al conectar con el backend.")
    } catch (e: IOException) {
        NetworkResult.Offline("Sin conexion o backend no disponible.")
    } catch (e: HttpException) {
        NetworkResult.Error(
            message = "Error HTTP ${e.code()} al comunicarse con el backend.",
            httpCode = e.code()
        )
    } catch (e: Exception) {
        NetworkResult.Error(e.message ?: "Error inesperado al comunicarse con el backend.")
    }
