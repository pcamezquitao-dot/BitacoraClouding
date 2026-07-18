package com.cactus.bitacora.data.local

import com.cactus.bitacora.model.BitacoraDiariaCreate
import org.junit.Assert.assertEquals
import org.junit.Test
import com.cactus.bitacora.location.LocationSnapshot

class BitacoraLocalEntityTest {
    @Test
    fun qrAreaSurvivesOfflineRoundTrip() {
        val request = BitacoraDiariaCreate(
            id_empleado = 10,
            id_supervisor = 20,
            client_uuid = "test-uuid",
            qr_area = "AREA_ADMINISTRATIVA|7|Operaciones"
        )

        val restored = request.toLocalEntity(
            syncStatus = SyncStatus.PENDIENTE_CREAR
        ).toCreateRequest()

        assertEquals(request, restored)
    }

    @Test
    fun openingAndClosingGpsArePersistedInLocalEntity() {
        val opening = LocationSnapshot(4.61, -74.08, 5.5f, 2600.0, 1000L, "fused")
        val closing = LocationSnapshot(4.62, -74.09, 6.5f, null, 2000L, "gps")

        val entity = BitacoraDiariaCreate(
            id_empleado = 10,
            client_uuid = "gps-test"
        ).toLocalEntity(
            syncStatus = SyncStatus.PENDIENTE_CREAR,
            openLocation = opening,
            closeLocation = closing
        )

        assertEquals(opening.latitude, entity.openLatitude!!, 0.0)
        assertEquals(opening.timestamp, entity.openGpsTimestamp)
        assertEquals(closing.longitude, entity.closeLongitude!!, 0.0)
        assertEquals(closing.provider, entity.closeLocationProvider)
    }
}
