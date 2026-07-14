package com.melodybubble.server.lounge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoungeGeometryTest {
    @Test
    fun `one user cannot open a lounge`() {
        assertThat(LoungeGeometry.circles(listOf(candidate("a", 0.0)))).isEmpty()
    }

    @Test
    fun `two users visible on the bubble map open a 50 meter lounge at a stable anchor`() {
        val circles = LoungeGeometry.circles(listOf(candidate("b", 8.0), candidate("a", 0.0)))

        assertThat(circles).containsExactly(LoungeCircle(37.0, 127.0, 50))
    }

    @Test
    fun `overlapping lounges use the midpoint and combined radius`() {
        val circles = LoungeGeometry.circles(
            listOf(candidate("a", 0.0), candidate("b", 8.0), candidate("c", 80.0), candidate("d", 88.0))
        )

        assertThat(circles).hasSize(1)
        assertThat(circles.single().radiusMeters).isEqualTo(100)
        assertThat(LoungeGeometry.distanceMeters(37.0, 127.0, circles.single().latitude, circles.single().longitude))
            .isBetween(39.0, 41.0)
    }

    private fun candidate(id: String, northMeters: Double) = LoungeCandidate(
        stableId = id,
        latitude = 37.0 + northMeters / 111_320.0,
        longitude = 127.0,
    )
}
