package com.example.myapplication

import com.example.myapplication.data.remote.BuildingLoungeApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealLoungeContractTest {
    @Test
    fun `production client exposes no fixture endpoint`() {
        val methodNames = BuildingLoungeApi::class.java.methods.map { it.name }

        assertFalse("fixture APIs must stay out of production", "createTestFixtures" in methodNames)
        assertTrue("real discovery must use nearby", "nearby" in methodNames)
    }
}
