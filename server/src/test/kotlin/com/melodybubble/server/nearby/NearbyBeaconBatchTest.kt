package com.melodybubble.server.nearby

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NearbyBeaconBatchTest {
    @Test
    fun `batch keeps the newest reading per beacon and processes newest reading last`() {
        val normalized = normalizeDirectProximityBatch(
            listOf(
                update("old-rotation", observedAt = 2_000L, sequence = 2L),
                update("new-rotation", observedAt = 3_000L, sequence = 3L),
                update("old-rotation", observedAt = 1_000L, sequence = 1L),
            ),
        )

        assertThat(normalized.map(DirectProximityUpdate::beaconId))
            .containsExactly("old-rotation", "new-rotation")
        assertThat(normalized.map(DirectProximityUpdate::observedAtEpochMillis))
            .containsExactly(2_000L, 3_000L)
    }

    @Test
    fun `batch cap retains the freshest unique beacons`() {
        val normalized = normalizeDirectProximityBatch(
            updates = (1L..45L).map { index ->
                update("beacon-$index", observedAt = index, sequence = index)
            },
            maxSize = 40,
        )

        assertThat(normalized).hasSize(40)
        assertThat(normalized.first().observedAtEpochMillis).isEqualTo(6L)
        assertThat(normalized.last().observedAtEpochMillis).isEqualTo(45L)
    }

    private fun update(beaconId: String, observedAt: Long, sequence: Long) =
        DirectProximityUpdate(
            beaconId = beaconId,
            proximity = ProximityBand.WITHIN_10M.name,
            confidence = DistanceConfidence.HIGH.name,
            method = "BLUETOOTH",
            sequence = sequence,
            observedAtEpochMillis = observedAt,
        )
}
