package com.cactus.bitacora

import com.cactus.bitacora.model.ManagementAggregateReportOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ManagementAggregateReportTest {
    @Test fun barsUseOneCommonScale() {
        assertEquals(1f, managementBarFraction(5634,5634),0.00001f)
        assertEquals(0.05591f, managementBarFraction(315,5634),0.00001f)
        assertEquals(0.09638f, managementBarFraction(543,5634),0.00001f)
        assertFalse(managementBarFraction(315,5634)==managementBarFraction(5634,5634))
    }

    @Test fun aggregateContractHasNoParticipantDetail() {
        val fields=ManagementAggregateReportOut::class.java.declaredFields.map{it.name}
        assertFalse(fields.any{it.contains("participante",ignoreCase=true)||it=="detalle"})
    }
}
