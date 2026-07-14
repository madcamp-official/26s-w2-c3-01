package com.example.myapplication.core.model

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
    clusterDistance: Float = 0.16f,
): List<NearbyMapMarker> {
    val positioned = listeners.map { it to it.mapPosition(zoomed) }
    if (zoomed) return positioned.map { (listener, position) -> NearbyMapMarker(listOf(listener), position) }

    val inner = positioned.filter { (listener, _) -> listener.proximity == Proximity.WITHIN_10M }
        .sortedBy { it.first.nearbyHandle }
    val outer = positioned.filter { (listener, _) -> listener.proximity == Proximity.WITHIN_20M }
    val remaining = inner.toMutableList()
    val markers = mutableListOf<NearbyMapMarker>()

    while (remaining.isNotEmpty()) {
        val group = mutableListOf(remaining.removeAt(0))
        var expanded = true
        while (expanded) {
            expanded = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (group.any { (_, position) -> position.distanceTo(candidate.second) < clusterDistance }) {
                    group += candidate
                    iterator.remove()
                    expanded = true
                }
            }
        }
        markers += NearbyMapMarker(
            listeners = group.map { it.first },
            position = DisplayPosition(
                x = group.map { it.second.x }.average().toFloat(),
                y = group.map { it.second.y }.average().toFloat(),
            ),
        )
    }

    markers += outer.map { (listener, position) -> NearbyMapMarker(listOf(listener), position) }
    return markers.sortedBy(NearbyMapMarker::stableKey)
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
