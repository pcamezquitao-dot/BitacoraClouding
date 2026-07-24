package com.cactus.bitacora.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitacoraDeletePolicyTest {
    @Test
    fun `404 means the remote record is already deleted`() {
        assertTrue(remoteDeleteAlreadySatisfied(404))
    }

    @Test
    fun `other HTTP errors must not be ignored`() {
        assertFalse(remoteDeleteAlreadySatisfied(400))
        assertFalse(remoteDeleteAlreadySatisfied(401))
        assertFalse(remoteDeleteAlreadySatisfied(500))
    }
}
