package com.cactus.bitacora

import com.cactus.bitacora.model.ReservoirSatelliteImageOut
import com.cactus.bitacora.model.ReservoirSatelliteOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservoirSatelliteNavigationTest {
    @Test
    fun satelliteModuleIsAvailableWithoutChangingAdministrativeRestrictions() {
        assertTrue(
            isScreenAllowed(
                AppEnvironment.SEGUIMIENTO_SATELITAL,
                AppScreen.ReservoirSatellite
            )
        )
        assertEquals(
            listOf(AppScreen.ReservoirSatellite),
            environmentMenuScreens(AppEnvironment.SEGUIMIENTO_SATELITAL)
        )
        assertTrue(
            AppEnvironment.entries
                .filterNot { it == AppEnvironment.SEGUIMIENTO_SATELITAL }
                .none { environmentMenuScreens(it).contains(AppScreen.ReservoirSatellite) }
        )
    }

    @Test
    fun satelliteContractSupportsDynamicCatalogAndImages() {
        val reservoir = ReservoirSatelliteOut(
            id_embalse = 7,
            nombre = "Embalse agregado desde MariaDB",
            pais = "Colombia"
        )
        val image = ReservoirSatelliteImageOut(
            id_imagen_satelital = 9,
            id_embalse = reservoir.id_embalse,
            fecha_captura = "2026-07-30",
            fuente = "Copernicus Sentinel-2",
            imagen_url = "/satelital-files/demo.png",
            mime_type = "image/png"
        )
        assertEquals(7, image.id_embalse)
        assertEquals("SIN_IMAGENES", reservoir.estado_seguimiento)
        assertEquals("DISPONIBLE", image.estado)
    }
}
