package com.example.myapplication.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val MAX_NEARBY_RADIUS_METERS = 20

val NearbyRingFractions = Proximity.entries.map(Proximity::outerRadiusFraction)

/** Stable demo/fallback placement. Its angle never derives from a physical bearing. */
fun abstractDisplayPosition(handle: String, proximity: Proximity): DisplayPosition {
    val angle = stableHash(handle, 360) * PI / 180.0
    val jitter = stableHash("radius:$handle", 1_000) / 1_000f
    val radius = when (proximity) {
        Proximity.WITHIN_10M -> 0.05f + jitter * 0.15f
        Proximity.WITHIN_20M -> 0.27f + jitter * 0.14f
    }
    return DisplayPosition(
        x = (0.5 + cos(angle) * radius).toFloat(),
        y = (0.5 + sin(angle) * radius).toFloat(),
    )
}

/** Picks a privacy-preserving point inside a band; callers keep it until the band changes. */
fun randomDisplayPosition(
    proximity: Proximity,
    random: Random = Random.Default,
): DisplayPosition {
    val angle = random.nextDouble(0.0, 2.0 * PI)
    val (innerRadius, outerRadius) = when (proximity) {
        Proximity.WITHIN_10M -> 0.05 to 0.20
        Proximity.WITHIN_20M -> 0.27 to 0.41
    }
    val radius = sqrt(
        random.nextDouble(
            innerRadius * innerRadius,
            outerRadius * outerRadius,
        ),
    )
    return DisplayPosition(
        x = (0.5 + cos(angle) * radius).toFloat(),
        y = (0.5 + sin(angle) * radius).toFloat(),
    )
}

fun DisplayPosition.radiusFromCenter(): Float {
    val dx = x - 0.5f
    val dy = y - 0.5f
    return sqrt(dx * dx + dy * dy)
}

private fun stableHash(value: String, modulo: Int): Int =
    (value.hashCode().toUInt().toLong() % modulo).toInt()
