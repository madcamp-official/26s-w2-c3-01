package com.example.myapplication

import com.example.myapplication.core.model.DisplayPosition
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.NearbyProximityConfidence
import com.example.myapplication.core.model.NearbyLoadState
import com.example.myapplication.core.model.NearbyMeasurementMethod
import com.example.myapplication.core.model.NearbyProximityStabilizer
import com.example.myapplication.core.model.NearbyRingFractions
import com.example.myapplication.core.model.Proximity
import com.example.myapplication.core.model.abstractDisplayPosition
import com.example.myapplication.core.model.radiusFromCenter
import com.example.myapplication.data.keepSettledDuringRefresh
import com.example.myapplication.data.toMeasurementMethod
import com.example.myapplication.nearby.BleBeaconCodec
import com.example.myapplication.nearby.BleRssiProximityEstimator
import com.example.myapplication.service.NearbyLocationPolicy
import com.example.myapplication.service.AccuracyFirstLocationSelector
import com.example.myapplication.service.NearbyLocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDistanceContractTest {
    @Test
    fun wireContractSupportsNewBandsAndOneReleaseOfLegacyValues() {
        assertEquals(Proximity.WITHIN_5M, Proximity.fromWire("WITHIN_5M"))
        assertEquals(Proximity.WITHIN_10M, Proximity.fromWire("WITHIN_10M"))
        assertEquals(Proximity.WITHIN_15M, Proximity.fromWire("WITHIN_15M"))
        assertEquals(Proximity.WITHIN_5M, Proximity.fromWire("VERY_CLOSE"))
        assertEquals(Proximity.WITHIN_10M, Proximity.fromWire("CLOSE"))
        assertEquals(Proximity.WITHIN_15M, Proximity.fromWire("AROUND"))
    }

    @Test
    fun ringsScaleInFiveMeterStepsAndAbstractDotsStayInsideTheirAnnulus() {
        assertEquals(listOf(0.143f, 0.286f, 0.429f), NearbyRingFractions)
        assertTrue(abstractDisplayPosition("mint", Proximity.WITHIN_5M).radiusFromCenter() in 0.05f..0.13f)
        assertTrue(abstractDisplayPosition("mint", Proximity.WITHIN_10M).radiusFromCenter() in 0.16f..0.27f)
        assertTrue(abstractDisplayPosition("mint", Proximity.WITHIN_15M).radiusFromCenter() in 0.30f..0.41f)
    }

    @Test
    fun existingBubbleNeedsTwoMatchingSnapshotsBeforeChangingRings() {
        val stabilizer = NearbyProximityStabilizer()
        val current = listOf(listener(Proximity.WITHIN_5M, DisplayPosition(0.55f, 0.5f)))
        val incoming = listOf(listener(Proximity.WITHIN_10M, DisplayPosition(0.70f, 0.5f)))

        val first = stabilizer.stabilize(current, incoming)
        val second = stabilizer.stabilize(first, incoming)

        assertEquals(Proximity.WITHIN_5M, first.single().proximity)
        assertEquals(current.single().displayPosition, first.single().displayPosition)
        assertEquals(Proximity.WITHIN_10M, second.single().proximity)
        assertEquals(incoming.single().displayPosition, second.single().displayPosition)
    }

    @Test
    fun lowConfidenceDistanceDoesNotMoveAnExistingBubble() {
        val stabilizer = NearbyProximityStabilizer(confirmationsRequired = 1)
        val current = listOf(listener(Proximity.WITHIN_5M, DisplayPosition(0.55f, 0.5f)))
        val noisy = listener(Proximity.WITHIN_15M, DisplayPosition(0.85f, 0.5f)).copy(
            proximityConfidence = NearbyProximityConfidence.LOW,
        )

        val result = stabilizer.stabilize(current, listOf(noisy)).single()

        assertEquals(Proximity.WITHIN_5M, result.proximity)
        assertEquals(current.single().displayPosition, result.displayPosition)
    }

    @Test
    fun locationPolicyUsesFastVisibleProfileAndRejectsStaleOrInaccurateFixes() {
        assertEquals(0L, NearbyLocationPolicy.INTERACTIVE.intervalMillis)
        assertEquals(0f, NearbyLocationPolicy.INTERACTIVE.minDistanceMeters)
        assertEquals(0L, NearbyLocationPolicy.EFFICIENT.intervalMillis)
        assertEquals(0f, NearbyLocationPolicy.EFFICIENT.minDistanceMeters)

        val now = 100_000L
        assertTrue(NearbyLocationPolicy.isUsable(now - 5_000L, 8f, now))
        assertFalse(NearbyLocationPolicy.isUsable(now - 16_000L, 8f, now))
        assertFalse(NearbyLocationPolicy.isUsable(now - 5_000L, 21f, now))
        assertFalse(NearbyLocationPolicy.isUsable(now - 5_000L, null, now))

        assertTrue(NearbyLocationPolicy.isUsableForInitialDiscovery(now - 20_000L, 45f, now))
        assertFalse(NearbyLocationPolicy.isUsableForInitialDiscovery(now - 31_000L, 45f, now))
        assertFalse(NearbyLocationPolicy.isUsableForInitialDiscovery(now - 5_000L, 51f, now))
    }

    @Test
    fun transientRefreshDoesNotReplaceASettledNearbyState() {
        assertEquals(
            NearbyLoadState.READY,
            NearbyLoadState.READY.keepSettledDuringRefresh(NearbyLoadState.LOADING),
        )
        assertEquals(
            NearbyLoadState.EMPTY,
            NearbyLoadState.EMPTY.keepSettledDuringRefresh(NearbyLoadState.ERROR),
        )
        assertEquals(
            NearbyLoadState.ERROR,
            NearbyLoadState.ERROR.keepSettledDuringRefresh(NearbyLoadState.LOADING),
        )
        assertEquals(
            NearbyLoadState.ERROR,
            NearbyLoadState.IDLE.keepSettledDuringRefresh(NearbyLoadState.ERROR),
        )
    }

    @Test
    fun locationProviderMapsToSafeDiagnosticMethod() {
        assertEquals(NearbyMeasurementMethod.GPS, "gps".toMeasurementMethod())
        assertEquals(NearbyMeasurementMethod.FUSED, "fused".toMeasurementMethod())
        assertEquals(NearbyMeasurementMethod.UNKNOWN, "network".toMeasurementMethod())
    }

    @Test
    fun accuracyFirstSelectorChoosesTheBestFreshCandidateOnce() {
        val selector = AccuracyFirstLocationSelector()
        selector.offer(sample(accuracy = 12f, elapsedNanos = 900_000_000L))
        selector.offer(sample(accuracy = 4f, elapsedNanos = 800_000_000L))

        assertEquals(4f, selector.takeBest(1_000_000_000L)?.accuracyMeters)
        assertEquals(null, selector.takeBest(1_100_000_000L))
        selector.offer(sample(accuracy = 1f, elapsedNanos = 800_000_000L))
        assertEquals(null, selector.takeBest(1_200_000_000L))
    }

    @Test
    fun bleBeaconPayloadRoundTripsTheOpaqueIdentifier() {
        val beaconId = "mb1_0123456789abcdef0123456789abcdef"

        assertEquals(beaconId, BleBeaconCodec.decode(BleBeaconCodec.encode(beaconId)))
        assertEquals(null, BleBeaconCodec.encode("profile-handle"))
    }

    @Test
    fun stableBleRssiSamplesProduceCoarseProximityBands() {
        val near = BleRssiProximityEstimator()
        val middle = BleRssiProximityEstimator()
        val outer = BleRssiProximityEstimator()

        repeat(5) {
            near.add("near", -65, it.toLong())
            middle.add("middle", -78, it.toLong())
            outer.add("outer", -83, it.toLong())
        }

        assertEquals(Proximity.WITHIN_5M, near.add("near", -65, 6L)?.proximity)
        assertEquals(Proximity.WITHIN_10M, middle.add("middle", -78, 6L)?.proximity)
        assertEquals(Proximity.WITHIN_15M, outer.add("outer", -83, 6L)?.proximity)
    }

    private fun sample(accuracy: Float, elapsedNanos: Long) = NearbyLocationSample(
        latitude = 37.0,
        longitude = 127.0,
        accuracyMeters = accuracy,
        observedAtEpochMillis = 1_000L,
        elapsedRealtimeNanos = elapsedNanos,
        source = "gps",
    )

    private fun listener(proximity: Proximity, position: DisplayPosition) = NearbyListener(
        nearbyHandle = "nearby-test",
        displayAlias = "Test",
        colorHex = 0xFF25C76FL,
        displayPosition = position,
        matchScore = 80,
        proximity = proximity,
        isPlaying = false,
        currentTrack = null,
        commonGenres = emptyList(),
    )
}
