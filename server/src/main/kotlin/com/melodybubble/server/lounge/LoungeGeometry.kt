package com.melodybubble.server.lounge

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class LoungeCandidate(val stableId: String, val latitude: Double, val longitude: Double)
internal data class LoungeCircle(val latitude: Double, val longitude: Double, val radiusMeters: Int)

/** Pure geometry used by the database-backed dynamic Wi-Fi lounge reconciler. */
internal object LoungeGeometry {
    private const val BUBBLE_RADIUS_METERS = 15.0
    private const val INITIAL_LOUNGE_RADIUS_METERS = 50

    fun circles(candidates: List<LoungeCandidate>): List<LoungeCircle> {
        val remaining = candidates.sortedBy(LoungeCandidate::stableId).toMutableList()
        val seeds = mutableListOf<LoungeCircle>()
        while (remaining.isNotEmpty()) {
            val component = mutableListOf(remaining.removeAt(0))
            var index = 0
            while (index < component.size) {
                val source = component[index++] 
                val connected = remaining.filter {
                    distanceMeters(source.latitude, source.longitude, it.latitude, it.longitude) <= BUBBLE_RADIUS_METERS
                }
                component += connected
                remaining.removeAll(connected.toSet())
            }
            if (component.size >= 2) {
                val anchor = component.minBy(LoungeCandidate::stableId)
                seeds += LoungeCircle(anchor.latitude, anchor.longitude, INITIAL_LOUNGE_RADIUS_METERS)
            }
        }
        return mergeOverlaps(seeds)
    }

    private fun mergeOverlaps(input: List<LoungeCircle>): List<LoungeCircle> {
        val circles = input.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            outer@ for (left in 0 until circles.size) {
                for (right in left + 1 until circles.size) {
                    val a = circles[left]
                    val b = circles[right]
                    if (distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) <= a.radiusMeters + b.radiusMeters) {
                        circles[left] = LoungeCircle(
                            latitude = (a.latitude + b.latitude) / 2.0,
                            longitude = (a.longitude + b.longitude) / 2.0,
                            radiusMeters = a.radiusMeters + b.radiusMeters,
                        )
                        circles.removeAt(right)
                        merged = true
                        break@outer
                    }
                }
            }
        }
        return circles.sortedWith(compareBy(LoungeCircle::latitude, LoungeCircle::longitude))
    }

    internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

