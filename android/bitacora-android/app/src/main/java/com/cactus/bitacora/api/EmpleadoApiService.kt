package com.cactus.bitacora.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface EmpleadoApiService {
    @GET("empleados/{id_empleado}/supervisor")
    suspend fun getSupervisorDeEmpleado(
        @Path("id_empleado") idEmpleado: Int
    ): Response<Unit>
}
