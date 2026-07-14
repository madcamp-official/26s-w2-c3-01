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
import com.example.myapplication.core.model.nearbyMapMarkers
import com.example.myapplication.core.model.radiusFromCenter
import com.example.myapplication.core.model.shouldZoomNearbyMap
import com.example.myapplication.data.keepSettledDuringRefresh
import com.example.myapplication.data.DirectNearbyCandidate
import com.example.myapplication.data.preferDirectNearbyUsers
import com.example.myapplication.data.shouldApplyDirectResolveResult
import com.example.myapplication.data.toMeasurementMethod
import com.example.myapplication.nearby.BleBeaconCodec
import com.example.myapplication.nearby.BleRssiProximityEstimator
import com.example.myapplication.nearby.PeerProximityMeasurement
import com.example.myapplication.service.NearbyLocationPolicy
import com.example.myapplication.service.AccuracyFirstLocationSelector
import com.example.myapplication.service.NearbyLocationSample
import com.example.myapplication.ui.screens.mapLocationMaxAccuracyMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDistanceContractTest {
    @Test
    fun wireContractSupportsNewBandsAndOneReleaseOfLegacyValues() {
        assertEquals(Proximity.WITHIN_10M, Proximity.fromWire("WITHIN_5M"))
        assertEquals(Proximity.WITHIN_10M, Proximity.fromWire("WITHIN_10M"))
        assertEquals(Proximity.WITHIN_20M, Proximity.fromWire("WITHIN_15M"))
        assertEquals(Proximity.WITHIN_20M, Proximity.fromWire("WITHIN_20M"))
        assertEquals(Proximity.WITHIN_10M, Proximity.fromWire("VERY_CLOSE"))
        assertEquals(Proximity.WITHIN_10M, Proximity.fromWire("CLOSE"))
        assertEquals(Proximity.WITHIN_20M, Proximity.fromWire("AROUND"))
    }

    @Test
    fun ringsUseTenAndTwentyMetersAndAbstractDotsStayInsideTheirAnnulus() {
        assertEquals(listOf(0.22f, 0.44f), NearbyRingFractions)
        assertTrue(abstractDisplayPosition("mint", Proximity.WITHIN_10M).radiusFromCenter() in 0.05f..0.20f)
        assertTrue(abstractDisplayPosition("mint", Proximity.WITHIN_20M).radiusFromCenter() in 0.27f..0.41f)
    }

    @Test
    fun existingBubbleNeedsTwoMatchingSnapshotsBeforeChangingRings() {
        val stabilizer = NearbyProximityStabilizer(
            positionForBand = { _, proximity ->
                if (proximity == Proximity.WITHIN_10M) DisplayPosition(0.55f, 0.5f)
                else DisplayPosition(0.80f, 0.5f)
            },
        )
        val current = listOf(listener(Proximity.WITHIN_10M, DisplayPosition(0.55f, 0.5f)))
        val incoming = listOf(listener(Proximity.WITHIN_20M, DisplayPosition(0.80f, 0.5f)))

        val first = stabilizer.stabilize(current, incoming)
        val second = stabilizer.stabilize(first, incoming)

        assertEquals(Proximity.WITHIN_10M, first.single().proximity)
        assertEquals(current.single().displayPosition, first.single().displayPosition)
        assertEquals(Proximity.WITHIN_20M, second.single().proximity)
        assertEquals(incoming.single().displayPosition, second.single().displayPosition)
    }

    @Test
    fun sameBandKeepsItsPinnedPointEvenWhenIncomingCoordinatesChange() {
        val stabilizer = NearbyProximityStabilizer(
            confirmationsRequired = 1,
            positionForBand = { _, _ -> DisplayPosition(0.60f, 0.5f) },
        )
        val initial = stabilizer.stabilize(
            current = emptyList(),
            incoming = listOf(listener(Proximity.WITHIN_10M, DisplayPosition(0.51f, 0.5f))),
        )
        val refreshed = stabilizer.stabilize(
            current = initial,
            incoming = listOf(listener(Proximity.WITHIN_10M, DisplayPosition(0.69f, 0.5f))),
        )

        assertEquals(DisplayPosition(0.60f, 0.5f), refreshed.single().displayPosition)
    }

    @Test
    fun lowConfidenceDistanceDoesNotMoveAnExistingBubble() {
        val stabilizer = NearbyProximityStabilizer(confirmationsRequired = 1)
        val current = listOf(listener(Proximity.WITHIN_10M, DisplayPosition(0.55f, 0.5f)))
        val noisy = listener(Proximity.WITHIN_20M, DisplayPosition(0.85f, 0.5f)).copy(
            proximityConfidence = NearbyProximityConfidence.LOW,
        )

        val result = stabilizer.stabilize(current, listOf(noisy)).single()

        assertEquals(Proximity.WITHIN_10M, result.proximity)
        assertEquals(current.single().displayPosition, result.displayPosition)
    }

    @Test
    fun transientMissingSnapshotKeepsBubbleUntilRetentionExpires() {
        var now = 1_000L
        val stabilizer = NearbyProximityStabilizer(
            confirmationsRequired = 1,
            missingRetentionMillis = 15_000L,
            nowMillis = { now },
        )
        val current = listOf(listener(Proximity.WITHIN_10M, DisplayPosition(0.55f, 0.5f)))

        assertEquals(current, stabilizer.stabilize(current, emptyList()))
        now += 14_999L
        val retained = stabilizer.stabilize(current, emptyList())
        assertEquals(current, retained)

        now += 1L
        assertTrue(stabilizer.stabilize(retained, emptyList()).isEmpty())
    }

    @Test
    fun returningBubbleClearsItsMissingTimer() {
        var now = 1_000L
        val stabilizer = NearbyProximityStabilizer(
            confirmationsRequired = 1,
            missingRetentionMillis = 15_000L,
            nowMillis = { now },
        )
        val current = listOf(listener(Proximity.WITHIN_10M, DisplayPosition(0.55f, 0.5f)))

        stabilizer.stabilize(current, emptyList())
        now += 10_000L
        val returned = stabilizer.stabilize(current, current)
        now += 10_000L

        assertEquals(returned, stabilizer.stabilize(returned, emptyList()))
    }

    @Test
    fun locationPolicyUsesFastVisibleProfileAndRejectsStaleOrInaccurateFixes() {
        assertEquals(5_000L, NearbyLocationPolicy.INTERACTIVE.intervalMillis)
        assertEquals(3_000L, NearbyLocationPolicy.INTERACTIVE.minIntervalMillis)
        assertEquals(3f, NearbyLocationPolicy.INTERACTIVE.minDistanceMeters)
        assertEquals(30_000L, NearbyLocationPolicy.EFFICIENT.intervalMillis)
        assertEquals(15_000L, NearbyLocationPolicy.EFFICIENT.minIntervalMillis)
        assertEquals(10f, NearbyLocationPolicy.EFFICIENT.minDistanceMeters)

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
    fun mapRecenteringAcceptsAndroidApproximateLocationWithoutWeakeningPreciseMode() {
        assertEquals(50f, mapLocationMaxAccuracyMeters(hasFineLocationPermission = true))
        assertEquals(5_000f, mapLocationMaxAccuracyMeters(hasFineLocationPermission = false))
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

        assertEquals(Proximity.WITHIN_10M, near.add("near", -65, 6L)?.proximity)
        assertEquals(Proximity.WITHIN_10M, middle.add("middle", -78, 6L)?.proximity)
        assertEquals(Proximity.WITHIN_20M, outer.add("outer", -83, 6L)?.proximity)
    }

    @Test
    fun mapZoomsWhenEveryoneIsInsideTenMetersAndKeepsListenersSeparate() {
        val listeners = listOf(
            listener(Proximity.WITHIN_10M, DisplayPosition(0.53f, 0.50f), "one"),
            listener(Proximity.WITHIN_10M, DisplayPosition(0.54f, 0.50f), "two"),
        )

        assertTrue(shouldZoomNearbyMap(listeners))
        val markers = nearbyMapMarkers(listeners)
        assertEquals(2, markers.size)
        assertTrue(markers.none { it.isCluster })
        assertEquals(0.56f, markers.first { it.listeners.single().nearbyHandle == "one" }.position.x, 0.001f)
    }

    @Test
    fun fullMapClustersOverlappingInnerListenersAndKeepsOuterListenersSeparate() {
        val listeners = listOf(
            listener(Proximity.WITHIN_10M, DisplayPosition(0.50f, 0.50f), "one"),
            listener(Proximity.WITHIN_10M, DisplayPosition(0.54f, 0.50f), "two"),
            listener(Proximity.WITHIN_10M, DisplayPosition(0.58f, 0.50f), "three"),
            listener(Proximity.WITHIN_20M, DisplayPosition(0.85f, 0.50f), "outer"),
        )

        assertFalse(shouldZoomNearbyMap(listeners))
        val markers = nearbyMapMarkers(listeners)
        assertEquals(2, markers.size)
        assertEquals(3, markers.single { it.isCluster }.listeners.size)
        assertEquals("outer", markers.single { !it.isCluster }.listeners.single().nearbyHandle)
    }

    @Test
    fun rotatingBeaconKeepsTheNewestMeasuredCandidateForTheSameUser() {
        val base = listener(Proximity.WITHIN_10M, DisplayPosition(0.55f, 0.5f))
        val measured = base.copy(
            proximity = Proximity.WITHIN_10M,
            displayPosition = DisplayPosition(0.7f, 0.5f),
            isDirectlyDetected = true,
        )
        val result = preferDirectNearbyUsers(
            candidates = listOf(
                DirectNearbyCandidate("old", base.copy(isDirectlyDetected = true), null),
                DirectNearbyCandidate(
                    "new",
                    measured,
                    PeerProximityMeasurement(
                        beaconId = "new",
                        proximity = Proximity.WITHIN_10M,
                        confidence = NearbyProximityConfidence.HIGH,
                        method = NearbyMeasurementMethod.BLUETOOTH,
                        observedAtEpochMillis = 2_000L,
                    ),
                ),
            ),
            currentByHandle = emptyMap(),
        )

        assertEquals(Proximity.WITHIN_10M, result.getValue(base.nearbyHandle).proximity)
    }

    @Test
    fun directMeasurementKeepsThePinnedPointWhileItStaysInTheSameBand() {
        val current = listener(Proximity.WITHIN_10M, DisplayPosition(0.55f, 0.5f))
        val refreshed = current.copy(
            displayPosition = DisplayPosition(0.70f, 0.5f),
            isDirectlyDetected = true,
        )

        val result = preferDirectNearbyUsers(
            candidates = listOf(DirectNearbyCandidate("beacon", refreshed, null)),
            currentByHandle = mapOf(current.nearbyHandle to current),
        ).getValue(current.nearbyHandle)

        assertEquals(current.displayPosition, result.displayPosition)
    }

    @Test
    fun lowConfidenceDirectBandDoesNotMoveAnExistingUser() {
        val current = listener(Proximity.WITHIN_10M, DisplayPosition(0.55f, 0.5f))
        val noisy = current.copy(
            proximity = Proximity.WITHIN_20M,
            proximityConfidence = NearbyProximityConfidence.LOW,
            displayPosition = DisplayPosition(0.85f, 0.5f),
            isDirectlyDetected = true,
        )
        val result = preferDirectNearbyUsers(
            candidates = listOf(
                DirectNearbyCandidate(
                    "beacon",
                    noisy,
                    PeerProximityMeasurement(
                        beaconId = "beacon",
                        proximity = Proximity.WITHIN_20M,
                        confidence = NearbyProximityConfidence.LOW,
                        method = NearbyMeasurementMethod.BLUETOOTH,
                        observedAtEpochMillis = 2_000L,
                    ),
                )
            ),
            currentByHandle = mapOf(current.nearbyHandle to current),
        ).getValue(current.nearbyHandle)

        assertEquals(current.proximity, result.proximity)
        assertEquals(current.displayPosition, result.displayPosition)
        assertTrue(result.isDirectlyDetected)
    }

    @Test
    fun staleDirectResolveResponseCannotRestoreAChangedOrStoppedBeaconSet() {
        val requested = setOf("old-beacon")
        assertTrue(
            shouldApplyDirectResolveResult(
                requestGeneration = 4L,
                currentGeneration = 4L,
                requestedBeaconIds = requested,
                desiredBeaconIds = requested,
                tokenIsCurrent = true,
                sharingIsActive = true,
            ),
        )
        assertFalse(
            shouldApplyDirectResolveResult(
                requestGeneration = 3L,
                currentGeneration = 4L,
                requestedBeaconIds = requested,
                desiredBeaconIds = setOf("new-beacon"),
                tokenIsCurrent = true,
                sharingIsActive = true,
            ),
        )
        assertFalse(
            shouldApplyDirectResolveResult(
                requestGeneration = 4L,
                currentGeneration = 4L,
                requestedBeaconIds = requested,
                desiredBeaconIds = requested,
                tokenIsCurrent = true,
                sharingIsActive = false,
            ),
        )
    }

    private fun sample(accuracy: Float, elapsedNanos: Long) = NearbyLocationSample(
        latitude = 37.0,
        longitude = 127.0,
        accuracyMeters = accuracy,
        observedAtEpochMillis = 1_000L,
        elapsedRealtimeNanos = elapsedNanos,
        source = "gps",
    )

    private fun listener(
        proximity: Proximity,
        position: DisplayPosition,
        handle: String = "nearby-test",
    ) = NearbyListener(
        nearbyHandle = handle,
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
