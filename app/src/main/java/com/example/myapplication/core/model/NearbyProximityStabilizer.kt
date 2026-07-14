package com.example.myapplication.core.model

/** Prevents a single noisy fix from moving an existing bubble between distance rings. */
class NearbyProximityStabilizer(
    private val confirmationsRequired: Int = 2,
) {
    private data class Pending(val proximity: Proximity, val count: Int)

    private val pendingByHandle = mutableMapOf<String, Pending>()

    @Synchronized
    fun stabilize(
        current: List<NearbyListener>,
        incoming: List<NearbyListener>,
    ): List<NearbyListener> {
        val currentByHandle = current.associateBy(NearbyListener::nearbyHandle)
        val incomingHandles = incoming.mapTo(mutableSetOf(), NearbyListener::nearbyHandle)
        pendingByHandle.keys.retainAll(incomingHandles)
        return incoming.map { next ->
            val previous = currentByHandle[next.nearbyHandle]
            if (previous == null || previous.proximity == next.proximity) {
                pendingByHandle.remove(next.nearbyHandle)
                return@map next
            }
            if (next.proximityConfidence == NearbyProximityConfidence.LOW) {
                return@map next.copy(
                    proximity = previous.proximity,
                    displayPosition = previous.displayPosition,
                )
            }
            val pending = pendingByHandle[next.nearbyHandle]
            val count = if (pending?.proximity == next.proximity) pending.count + 1 else 1
            if (count >= confirmationsRequired) {
                pendingByHandle.remove(next.nearbyHandle)
                next
            } else {
                pendingByHandle[next.nearbyHandle] = Pending(next.proximity, count)
                next.copy(
                    proximity = previous.proximity,
                    displayPosition = previous.displayPosition,
                )
            }
        }
    }

    @Synchronized
    fun clear() = pendingByHandle.clear()
}
