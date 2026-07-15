package com.example.myapplication.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NearbyMapMarker(
    val listeners: List<NearbyListener>,
    val position: DisplayPosition,
) {
    val isCluster: Boolean get() = listeners.size > 1
    val stableKey: String get() = listeners.joinToString("|") { it.nearbyHandle }
}

fun shouldZoomNearbyMap(listeners: List<NearbyListener>): Boolean =
    listeners.isNotEmpty() && listeners.all { it.proximity == Proximity.WITHIN_10M }

fun nearbyMapMarkers(
    listeners: List<NearbyListener>,
    zoomed: Boolean = shouldZoomNearbyMap(listeners),
    minimumDistance: Float = 0.16f,
): List<NearbyMapMarker> {
    val placed = mutableListOf<DisplayPosition>()
    return listeners.sortedBy(NearbyListener::nearbyHandle).map { listener ->
        val desired = listener.mapPosition(zoomed)
        val candidates = listOf(desired) + placementCandidates(listener, zoomed)
        val position = candidates.firstOrNull { candidate ->
            placed.all { it.distanceTo(candidate) >= minimumDistance }
        } ?: candidates.maxBy { candidate ->
            placed.minOfOrNull { it.distanceTo(candidate) } ?: Float.MAX_VALUE
        }
        placed += position
        NearbyMapMarker(listOf(listener), position)
    }
}

private fun placementCandidates(listener: NearbyListener, zoomed: Boolean): List<DisplayPosition> {
    val radii = when {
        zoomed -> listOf(0.14f, 0.27f, 0.40f)
        listener.proximity == Proximity.WITHIN_10M -> listOf(0.09f, 0.19f)
        else -> listOf(0.29f, 0.40f)
    }
    val angleOffset = ((listener.nearbyHandle.hashCode().toUInt().toLong() % 360L) * PI / 180.0)
    return radii.flatMapIndexed { ringIndex, radius ->
        val slots = if (radius < 0.15f) 8 else if (radius < 0.30f) 12 else 18
        (0 until slots).map { slot ->
            val angle = angleOffset + (2.0 * PI * slot / slots) + ringIndex * 0.17
            DisplayPosition(
                x = (0.5 + cos(angle) * radius).toFloat(),
                y = (0.5 + sin(angle) * radius).toFloat(),
            )
        }
    }
}

private fun NearbyListener.mapPosition(zoomed: Boolean): DisplayPosition {
    if (!zoomed || proximity != Proximity.WITHIN_10M) return displayPosition
    val dx = displayPosition.x - 0.5f
    val dy = displayPosition.y - 0.5f
    return DisplayPosition(
        x = 0.5f + dx * NEARBY_INNER_ZOOM_SCALE,
        y = 0.5f + dy * NEARBY_INNER_ZOOM_SCALE,
    )
}

private fun DisplayPosition.distanceTo(other: DisplayPosition): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

private const val NEARBY_INNER_ZOOM_SCALE = 2f
