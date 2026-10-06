package com.cactus.bitacora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyLogRolePolicyTest {
    private val supervisorType = 2
    private val managerType = 4

    @Test
    fun supervisorCanBeDailyLogParticipant() {
        assertTrue(
            assignmentAllowsTarget(
                cargo = supervisorType,
                target = DailyLogQrTarget.EMPLEADO,
                supervisorTypeCode = supervisorType
            )
        )
    }

    @Test
    fun managerCanBeDailyLogParticipant() {
        assertTrue(
            assignmentAllowsTarget(
                cargo = managerType,
                target = DailyLogQrTarget.EMPLEADO,
                supervisorTypeCode = supervisorType
            )
        )
    }

    @Test
    fun supervisorCanBeResponsibleForAnotherParticipant() {
        assertTrue(
            assignmentAllowsDailyLogResponsible(
                cargo = supervisorType,
                employeeId = 20,
                responsibleId = 10,
                supervisorTypeCode = supervisorType,
                managerTypeCode = managerType
            )
        )
    }

    @Test
    fun supervisorCannotBeResponsibleForSelf() {
        assertFalse(
            assignmentAllowsDailyLogResponsible(
                cargo = supervisorType,
                employeeId = 10,
                responsibleId = 10,
                supervisorTypeCode = supervisorType,
                managerTypeCode = managerType
            )
        )
    }

    @Test
    fun managerCanBeResponsibleForSelf() {
        assertTrue(
            assignmentAllowsDailyLogResponsible(
                cargo = managerType,
                employeeId = 10,
                responsibleId = 10,
                supervisorTypeCode = supervisorType,
                managerTypeCode = managerType
            )
        )
    }

    @Test
    fun otherRoleCannotBeDailyLogResponsible() {
        assertFalse(
            assignmentAllowsDailyLogResponsible(
                cargo = 1,
                employeeId = 20,
                responsibleId = 10,
                supervisorTypeCode = supervisorType,
                managerTypeCode = managerType
            )
        )
    }
}
