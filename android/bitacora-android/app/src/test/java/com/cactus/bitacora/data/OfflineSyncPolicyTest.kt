package com.cactus.bitacora.data

import com.cactus.bitacora.data.local.SyncStatus
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class OfflineSyncPolicyTest {
    @Test
    fun `falta de red y error servidor requieren reintento`() {
        assertTrue(IOException("sin red").isRetryableSyncError())
        assertTrue(httpError(408).isRetryableSyncError())
        assertTrue(httpError(503).isRetryableSyncError())
        assertTrue(httpError(500).isRetryableSyncError())
        assertTrue(httpError(429).isRetryableSyncError())
    }

    @Test
    fun `rechazo funcional del servidor queda como error permanente`() {
        assertFalse(httpError(400).isRetryableSyncError())
        assertFalse(httpError(401).isRetryableSyncError())
        assertFalse(httpError(403).isRetryableSyncError())
        assertFalse(httpError(404).isRetryableSyncError())
        assertFalse(httpError(409).isRetryableSyncError())
        assertFalse(httpError(413).isRetryableSyncError())
        assertFalse(httpError(415).isRetryableSyncError())
        assertFalse(httpError(422).isRetryableSyncError())
    }

    @Test
    fun `maquina offline first contiene todos los estados requeridos`() {
        val names = SyncStatus.entries.map { it.name }.toSet()
        assertTrue("PENDIENTE_CREAR" in names)
        assertTrue("PENDIENTE_ACTUALIZAR" in names)
        assertTrue("PENDIENTE_ELIMINAR" in names)
        assertTrue("SINCRONIZADO" in names)
        assertTrue("ERROR" in names)
    }

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Unit>(code, "error".toResponseBody()))
}
