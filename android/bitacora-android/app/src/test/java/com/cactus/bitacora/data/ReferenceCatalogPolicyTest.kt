package com.cactus.bitacora.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceCatalogPolicyTest {
    @Test
    fun participantCodesAreTrimmedUppercaseAndRemainAlphanumeric() {
        assertEquals("P0002", normalizeCatalogCode(" p0002 "))
    }

    @Test
    fun areaCodeParsesIdWithoutTrustingSuppliedName() {
        assertEquals(12, parseAreaCode(" AREA_ADMINISTRATIVA | 12 | Nombre falso ").id)
    }

    @Test
    fun invalidAreaCodeIsRejected() {
        assertThrows(CatalogValidationException::class.java) {
            parseAreaCode("AREA|ABC|Administración")
        }
    }

    @Test
    fun supervisorRequiresCargoThree() {
        assertTrue(assignmentAllowsRole(3, CatalogRole.SUPERVISOR))
        assertFalse(assignmentAllowsRole(1, CatalogRole.SUPERVISOR))
    }

    @Test
    fun employeeRejectsSupervisorAndManagerRoles() {
        assertTrue(assignmentAllowsRole(1, CatalogRole.EMPLOYEE))
        assertFalse(assignmentAllowsRole(3, CatalogRole.EMPLOYEE))
        assertFalse(assignmentAllowsRole(4, CatalogRole.EMPLOYEE))
    }

    @Test
    fun missingOfflineCodeUsesFunctionalMessageWithoutNetworkDetails() {
        val message = ReferenceCatalogRepository.MISSING_LOCAL_MESSAGE
        assertTrue(message.contains("catálogo local"))
        assertFalse(message.contains("Failed to connect"))
        assertFalse(message.contains("161.22.47.89"))
    }

    @Test
    fun canonicalAreaCodeUsesServerName() {
        assertEquals(
            "AREA_ADMINISTRATIVA|7|Administración",
            ReferenceCatalogRepository.canonicalAreaCode(7, " Administración ")
        )
    }
}
