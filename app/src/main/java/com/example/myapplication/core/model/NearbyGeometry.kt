package com.example.myapplication.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val MAX_NEARBY_RADIUS_METERS = 15

val NearbyRingFractions = Proximity.entries.map(Proximity::outerRadiusFraction)

/** Stable demo/fallback placement. Its angle never derives from a physical bearing. */
fun abstractDisplayPosition(handle: String, proximity: Proximity): DisplayPosition {
    val angle = stableHash(handle, 360) * PI / 180.0
    val jitter = stableHash("radius:$handle", 1_000) / 1_000f
    val radius = when (proximity) {
        Proximity.WITHIN_5M -> 0.05f + jitter * 0.08f
        Proximity.WITHIN_10M -> 0.16f + jitter * 0.11f
        Proximity.WITHIN_15M -> 0.30f + jitter * 0.11f
    }
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
