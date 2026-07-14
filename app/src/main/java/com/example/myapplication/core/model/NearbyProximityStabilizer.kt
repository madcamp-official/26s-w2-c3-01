package com.example.myapplication.core.model

/** Prevents a single noisy fix from moving an existing bubble between distance rings. */
class NearbyProximityStabilizer(
    private val confirmationsRequired: Int = 2,
    private val missingRetentionMillis: Long = 15_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Pending(val proximity: Proximity, val count: Int)

    private val pendingByHandle = mutableMapOf<String, Pending>()
    private val missingSinceByHandle = mutableMapOf<String, Long>()

    @Synchronized
    fun stabilize(
        current: List<NearbyListener>,
        incoming: List<NearbyListener>,
    ): List<NearbyListener> {
        val now = nowMillis()
        val currentByHandle = current.associateBy(NearbyListener::nearbyHandle)
        val incomingHandles = incoming.mapTo(mutableSetOf(), NearbyListener::nearbyHandle)
        pendingByHandle.keys.retainAll(incomingHandles)
        missingSinceByHandle.keys.retainAll(currentByHandle.keys)
        incomingHandles.forEach(missingSinceByHandle::remove)
        val stabilizedIncoming = incoming.map { next ->
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
        val retainedMissing = current.filter { previous ->
            if (previous.nearbyHandle in incomingHandles) return@filter false
            val missingSince = missingSinceByHandle.getOrPut(previous.nearbyHandle) { now }
            val shouldRetain = now - missingSince < missingRetentionMillis
            if (!shouldRetain) missingSinceByHandle.remove(previous.nearbyHandle)
            shouldRetain
        }
        return stabilizedIncoming + retainedMissing
    }

    /** Explicit server-side departures must bypass transient radio/location-loss retention. */
    @Synchronized
    fun remove(
        current: List<NearbyListener>,
        handles: Collection<String>,
    ): List<NearbyListener> {
        val removed = handles.toSet()
        pendingByHandle.keys.removeAll(removed)
        missingSinceByHandle.keys.removeAll(removed)
        return current.filterNot { it.nearbyHandle in removed }
    }

    @Synchronized
    fun clear() {
        pendingByHandle.clear()
        missingSinceByHandle.clear()
    }
}
