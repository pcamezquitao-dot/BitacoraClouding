package com.cactus.bitacora

import com.cactus.bitacora.biometric.technical.FaceFlowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceNavigationStructureTest {
    @Test
    fun facialEnrollmentHasIndependentMainDestination() {
        assertTrue(AppScreen.entries.contains(AppScreen.FaceEnrollment))
    }

    @Test
    fun enrollmentIsIndependentFromParticipantRole() {
        assertEquals("Participante", ENROLLMENT_IDENTITY_SCOPE)
    }

    @Test
    fun backReturnsToMainHealthDestination() {
        assertEquals(AppScreen.Health, mainDestinationAfterBack())
    }

    @Test
    fun redundantQrAreaDestinationWasRemoved() {
        assertFalse(AppScreen.entries.any { it.name == "QrArea" })
    }

    @Test
    fun dailyLogUsesFaceOnlyForIdentification() {
        assertEquals(FaceFlowMode.IDENTIFICATION, DAILY_LOG_FACE_MODE)
    }

    @Test
    fun validAreaSelectionStillRequiresAssignedAreaMatch() {
        assertTrue(areaMatchesAssignment(selectedAreaId = 7, assignedAreaId = 7))
        assertFalse(areaMatchesAssignment(selectedAreaId = 7, assignedAreaId = 8))
        assertFalse(areaMatchesAssignment(selectedAreaId = null, assignedAreaId = 7))
    }

    @Test
    fun recognizedParticipantRoleIsResolvedAfterIdentityMatch() {
        assertTrue(assignmentAllowsTarget(cargo = 2, target = DailyLogQrTarget.EMPLEADO))
        assertTrue(assignmentAllowsTarget(cargo = 3, target = DailyLogQrTarget.SUPERVISOR))
        assertFalse(assignmentAllowsTarget(cargo = 3, target = DailyLogQrTarget.EMPLEADO))
        assertEquals(
            "El participante reconocido no tiene el rol activo requerido: Supervisor",
            missingRoleMessage(DailyLogQrTarget.SUPERVISOR)
        )
    }

    @Test
    fun participantMayHaveMultipleRolesOrNoActiveRoleWithoutBlockingEnrollment() {
        assertEquals("Empleado/otro (cargo 2)", roleLabel(2))
        assertEquals("Supervisor", roleLabel(3))
        assertEquals("Rol no especificado", roleLabel(null))
    }
}
