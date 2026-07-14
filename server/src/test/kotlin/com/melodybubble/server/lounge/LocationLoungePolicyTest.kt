package com.melodybubble.server.lounge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.math.PI

class LocationLoungePolicyTest {
    @Test
    fun `new lounge starts at twenty meters for its creation grace period`() {
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")

        assertThat(LocationLoungePolicy.INITIAL_RADIUS_METERS).isEqualTo(20)
        assertThat(
            LocationLoungePolicy.effectiveRadius(
                currentRadius = 20,
                distancesMeters = listOf(0.0),
                createdAt = createdAt,
                now = createdAt.plusSeconds(59),
            )
        ).isEqualTo(20)
        assertThat(
            LocationLoungePolicy.effectiveRadius(
                currentRadius = 20,
                distancesMeters = listOf(0.0),
                createdAt = createdAt,
                now = createdAt.plusSeconds(60),
            )
        ).isEqualTo(5)
    }

    @Test
    fun `radius thresholds are 5 10 and 20 meters and capped`() {
        assertThat(LocationLoungePolicy.radiusFor(0)).isEqualTo(5)
        assertThat(LocationLoungePolicy.radiusFor(4)).isEqualTo(5)
        assertThat(LocationLoungePolicy.radiusFor(5)).isEqualTo(10)
        assertThat(LocationLoungePolicy.radiusFor(9)).isEqualTo(10)
        assertThat(LocationLoungePolicy.radiusFor(10)).isEqualTo(20)
        assertThat(LocationLoungePolicy.radiusFor(10_000)).isEqualTo(20)
    }

    @Test
    fun `radius expands and shrinks from actual distances`() {
        assertThat(LocationLoungePolicy.stableRadius(5, List(5) { 2.0 })).isEqualTo(10)
        assertThat(LocationLoungePolicy.stableRadius(10, List(10) { 2.0 })).isEqualTo(20)
        assertThat(LocationLoungePolicy.stableRadius(20, List(8) { 2.0 })).isEqualTo(10)
        assertThat(LocationLoungePolicy.stableRadius(10, List(3) { 2.0 })).isEqualTo(5)
    }

    @Test
    fun `radius boundary population terminates deterministically without oscillation`() {
        val distances = List(4) { 2.0 } + List(2) { 8.0 }
        assertThat(LocationLoungePolicy.stableRadius(5, distances)).isEqualTo(5)
        assertThat(LocationLoungePolicy.stableRadius(10, distances)).isEqualTo(10)
    }

    @Test
    fun `disjoint and contained circle intersections are exact`() {
        assertThat(LocationLoungePolicy.circleIntersectionArea(5.0, 5.0, 10.0)).isZero()
        assertThat(LocationLoungePolicy.circleIntersectionArea(5.0, 20.0, 0.0))
            .isCloseTo(PI * 25.0, org.assertj.core.data.Offset.offset(1e-9))
    }

    @Test
    fun `directional overlap treats source and target independently`() {
        assertThat(LocationLoungePolicy.directionalOverlap(5.0, 20.0, 0.0)).isEqualTo(1.0)
        assertThat(LocationLoungePolicy.directionalOverlap(20.0, 5.0, 0.0)).isEqualTo(0.0625)
    }

    @Test
    fun `exactly 70 percent merges while 69 point 9 percent remains`() {
        val d70 = distanceForEqualCircleRatio(0.70)
        val d699 = distanceForEqualCircleRatio(0.699)
        assertThat(LocationLoungePolicy.directionalOverlap(10.0, 10.0, d70)).isCloseTo(0.70, offset())
        assertThat(LocationLoungePolicy.directionalOverlap(10.0, 10.0, d699)).isLessThan(0.70)
    }

    @Test
    fun `one directional threshold deletes only the contained source`() {
        val olderLarge = disk("00000000-0000-0000-0000-000000000001", 20, Instant.EPOCH)
        val newerSmall = disk("00000000-0000-0000-0000-000000000002", 5, Instant.EPOCH.plusSeconds(1))
        assertThat(LocationLoungePolicy.chooseMerge(newerSmall, listOf(olderLarge))?.targetId)
            .isEqualTo(olderLarge.id)
        assertThat(LocationLoungePolicy.chooseMerge(olderLarge, listOf(newerSmall))).isNull()
    }

    @Test
    fun `mutual threshold keeps earlier lounge`() {
        val older = disk("00000000-0000-0000-0000-000000000002", 10, Instant.EPOCH)
        val newer = disk("00000000-0000-0000-0000-000000000001", 10, Instant.EPOCH.plusSeconds(1))
        assertThat(LocationLoungePolicy.chooseMerge(older, listOf(newer))).isNull()
        assertThat(LocationLoungePolicy.chooseMerge(newer, listOf(older))?.targetId).isEqualTo(older.id)
    }

    @Test
    fun `mutual threshold tie keeps smaller lounge id`() {
        val smaller = disk("00000000-0000-0000-0000-000000000001", 10, Instant.EPOCH)
        val larger = disk("00000000-0000-0000-0000-000000000002", 10, Instant.EPOCH)
        assertThat(LocationLoungePolicy.chooseMerge(smaller, listOf(larger))).isNull()
        assertThat(LocationLoungePolicy.chooseMerge(larger, listOf(smaller))?.targetId).isEqualTo(smaller.id)
    }

    @Test
    fun `multiple targets choose highest directional overlap`() {
        val source = disk("00000000-0000-0000-0000-000000000010", 5, Instant.EPOCH.plusSeconds(5))
        val complete = disk("00000000-0000-0000-0000-000000000011", 20, Instant.EPOCH)
        val partial = disk("00000000-0000-0000-0000-000000000012", 8, Instant.EPOCH, northMeters = 3.0)
        assertThat(LocationLoungePolicy.chooseMerge(source, listOf(partial, complete))?.targetId)
            .isEqualTo(complete.id)
    }

    @Test
    fun `equal overlap target tie uses creation time then id`() {
        val source = disk("00000000-0000-0000-0000-000000000010", 5, Instant.EPOCH.plusSeconds(10))
        val oldLargeId = disk("00000000-0000-0000-0000-000000000099", 20, Instant.EPOCH)
        val newSmallId = disk("00000000-0000-0000-0000-000000000001", 20, Instant.EPOCH.plusSeconds(1))
        assertThat(LocationLoungePolicy.chooseMerge(source, listOf(newSmallId, oldLargeId))?.targetId)
            .isEqualTo(oldLargeId.id)

        val low = disk("00000000-0000-0000-0000-000000000001", 20, Instant.EPOCH)
        assertThat(LocationLoungePolicy.chooseMerge(source, listOf(oldLargeId, low))?.targetId).isEqualTo(low.id)
    }

    @Test
    fun `normal room capacity remains five even though merge transfer may exceed it`() {
        assertThat(LocationLoungePolicy.MAX_CHAT_ROOMS).isEqualTo(5)
    }

    private fun disk(id: String, radius: Int, created: Instant, northMeters: Double = 0.0) = LoungeDisk(
        UUID.fromString(id),
        37.0 + northMeters / 111_320.0,
        127.0,
        radius,
        created,
    )

    private fun distanceForEqualCircleRatio(ratio: Double): Double {
        var low = 0.0
        var high = 20.0
        repeat(100) {
            val mid = (low + high) / 2
            if (LocationLoungePolicy.directionalOverlap(10.0, 10.0, mid) > ratio) low = mid else high = mid
        }
        return (low + high) / 2
    }

    private fun offset() = org.assertj.core.data.Offset.offset(1e-9)
}
