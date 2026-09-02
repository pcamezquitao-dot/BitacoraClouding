package com.cactus.bitacora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerNavigationStructureTest {
    @Test fun workerIsASeparateMainModule() {
        assertTrue(AppEnvironment.entries.any { it.label == "Trabajador" })
        assertFalse(AppScreen.entries.any { it.name.contains("WorkerTime", true) })
    }
}
