package com.melodybubble.server.nearby

import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NearbyDistanceContractTest {
    @Test
    fun `classifies only the ten and twenty meter bands`() {
        assertEquals(ProximityBand.WITHIN_10M, proximityBand(0.0))
        assertEquals(ProximityBand.WITHIN_10M, proximityBand(5.0))
        assertEquals(ProximityBand.WITHIN_10M, proximityBand(10.0))
        assertEquals(ProximityBand.WITHIN_20M, proximityBand(10.01))
        assertEquals(ProximityBand.WITHIN_20M, proximityBand(20.0))
        assertNull(proximityBand(20.01))
        assertNull(proximityBand(Double.NaN))
    }

    @Test
    fun `abstract coordinates stay in their radial annulus and remain stable`() {
        val handle = "n_test_opaque_handle"
        val ten = abstractPosition(handle, ProximityBand.WITHIN_10M)
        val twenty = abstractPosition(handle, ProximityBand.WITHIN_20M)

        assertEquals(ten, abstractPosition(handle, ProximityBand.WITHIN_10M))
        assertTrue(ten.radius() in 0.05f..0.20f)
        assertTrue(twenty.radius() in 0.27f..0.41f)
    }

    @Test
    fun `distance confidence is high only when uncertainty stays inside one band`() {
        val accuracy = combinedHorizontalAccuracyMeters(1.0, 1.0)

        assertEquals(DistanceConfidence.HIGH, distanceConfidence(5.0, accuracy))
        assertEquals(DistanceConfidence.HIGH, distanceConfidence(15.0, accuracy))
        assertEquals(DistanceConfidence.LOW, distanceConfidence(10.0, accuracy))
        assertEquals(DistanceConfidence.LOW, distanceConfidence(20.0, accuracy))
        assertEquals(DistanceConfidence.UNKNOWN, distanceConfidence(15.0, null))
    }

    private fun AbstractPosition.radius(): Float {
        val dx = x - 0.5f
        val dy = y - 0.5f
        return sqrt(dx * dx + dy * dy)
    }
}
