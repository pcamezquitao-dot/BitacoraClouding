package com.cactus.bitacora.data

import com.cactus.bitacora.data.models.AreaByQrIn
import com.cactus.bitacora.data.models.BitacoraDiariaCreate

class BitacoraRepository(
    private val api: BitacoraApi = Api.create()
) {
    suspend fun checkHealth() =
        api.health()

    suspend fun getAreaByQr(qr: String) =
        api.getAreaByQr(AreaByQrIn(qr))

    suspend fun crearBitacoraDiaria(request: BitacoraDiariaCreate) =
        api.crearBitacoraDiaria(request)

    suspend fun getBitacoraDiaria(idBitacora: Int) =
        api.getBitacoraDiaria(idBitacora)
}