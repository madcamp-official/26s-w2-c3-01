package com.example.myapplication.offlineexchange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeCompletionGateTest {
    @Test
    fun `record commits only after acknowledgements from both devices`() {
        val gate = ExchangeCompletionGate()
        assertFalse(gate.canCommit)
        gate.markLocalAck()
        assertFalse(gate.canCommit)
        gate.markRemoteAck()
        assertTrue(gate.canCommit)
    }

    @Test
    fun `connection loss prevents an incomplete record from committing`() {
        val gate = ExchangeCompletionGate()
        gate.markLocalAck()
        gate.disconnect()
        gate.markRemoteAck()
        assertFalse(gate.canCommit)
    }
}
