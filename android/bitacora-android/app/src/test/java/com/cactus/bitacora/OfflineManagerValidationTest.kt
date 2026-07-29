package com.cactus.bitacora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineManagerValidationTest {
    private val today = "2026-07-28"

    @Test
    fun p0001ActiveAndCurrentCargoThreeIsAcceptedOffline() {
        assertTrue(manager(qr = " p0001 ", id = 1, cargo = 3))
    }

    @Test
    fun anotherActiveCargoThreeIsRejectedAsManager() {
        assertFalse(manager(qr = "P0005", id = 5, cargo = 3))
    }

    @Test
    fun activeCurrentCargoFourRemainsAccepted() {
        assertTrue(manager(qr = "P0099", id = 99, cargo = 4))
    }

    @Test
    fun inactiveP0001IsRejected() {
        assertFalse(manager(qr = "P0001", id = 1, cargo = 3, active = false))
    }

    @Test
    fun expiredP0001IsRejected() {
        assertFalse(manager(qr = "P0001", id = 1, cargo = 3, end = "2026-07-27"))
    }

    @Test
    fun nullEndDateIsCurrent() {
        assertTrue(manager(qr = "P0001", id = 1, cargo = 3, end = null))
    }

    @Test
    fun futureStartDateIsRejected() {
        assertFalse(manager(qr = "P0001", id = 1, cargo = 3, start = "2026-07-29"))
    }

    @Test
    fun normalSupervisorSelectionRemainsCargoThreeOnly() {
        assertTrue(assignmentAllowsTarget(3, DailyLogQrTarget.SUPERVISOR))
        assertFalse(assignmentAllowsTarget(4, DailyLogQrTarget.SUPERVISOR))
    }

    private fun manager(
        qr: String,
        id: Int,
        cargo: Int?,
        active: Boolean = true,
        start: String? = null,
        end: String? = null
    ) = allowsOfflineManager(
        normalizedQr = qr,
        participantId = id,
        cargo = cargo,
        active = active,
        startDate = start,
        endDate = end,
        today = today
    )
}
