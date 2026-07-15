package com.melodybubble.server.lounge

import java.time.Instant
import java.util.UUID
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class LoungeDisk(
    val id: UUID,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val createdAt: Instant,
)

data class LoungeMergeDecision(val deletedId: UUID, val targetId: UUID, val overlapRatio: Double)

object LocationLoungePolicy {
    const val MIN_RADIUS_METERS = 5
    const val INITIAL_RADIUS_METERS = 20
    const val MAX_RADIUS_METERS = 20
    const val INITIAL_RADIUS_GRACE_SECONDS = 60L
    const val EXIT_GRACE_SECONDS = 60L
    const val AUTO_DELETE_GRACE_SECONDS = 180L
    const val OVERLAP_THRESHOLD = 0.70
    const val MAX_CHAT_ROOMS = 5

    fun canCreateLounge(containingActiveLoungeCount: Int): Boolean = containingActiveLoungeCount == 0

    fun isIncluded(distanceMeters: Double, radiusMeters: Int, locationFresh: Boolean): Boolean =
        locationFresh && distanceMeters <= radiusMeters + 1e-7

    fun shouldRetainPresence(
        currentlyInside: Boolean,
        outsideSince: Instant?,
        now: Instant,
    ): Boolean = currentlyInside || outsideSince == null || outsideSince.plusSeconds(EXIT_GRACE_SECONDS).isAfter(now)

    fun canCreateChatRoom(activeRoomCount: Int, loungeStatus: LocationLoungeStatus): Boolean =
        loungeStatus == LocationLoungeStatus.ACTIVE && activeRoomCount < MAX_CHAT_ROOMS

    fun canDeleteChatRoom(ownerId: UUID, actorId: UUID): Boolean = ownerId == actorId

    fun shouldAutoDelete(
        currentUserCount: Int,
        createdAt: Instant,
        now: Instant,
        status: LocationLoungeStatus,
    ): Boolean = status == LocationLoungeStatus.ACTIVE && currentUserCount <= 2 &&
        !createdAt.plusSeconds(AUTO_DELETE_GRACE_SECONDS).isAfter(now)

    fun radiusFor(userCount: Int): Int = when {
        userCount >= 10 -> 20
        userCount >= 5 -> 10
        else -> 5
    }

    /**
     * Re-evaluates a radius from a fixed set. Monotonic distance counts converge in at most
     * three transitions; the visited guard additionally makes malformed input fail stable.
     */
    fun stableRadius(currentRadius: Int, distancesMeters: Collection<Double>): Int {
        var radius = currentRadius.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val visited = linkedSetOf<Int>()
        repeat(4) {
            if (!visited.add(radius)) return visited.min()
            val count = distancesMeters.count { it <= radius + 1e-7 }
            val next = radiusFor(count)
            if (next == radius) return radius
            radius = next
        }
        return radius
    }

    fun effectiveRadius(
        currentRadius: Int,
        distancesMeters: Collection<Double>,
        createdAt: Instant,
        now: Instant,
    ): Int = if (createdAt.plusSeconds(INITIAL_RADIUS_GRACE_SECONDS).isAfter(now)) {
        INITIAL_RADIUS_METERS
    } else {
        stableRadius(currentRadius, distancesMeters)
    }

    fun circleIntersectionArea(radiusA: Double, radiusB: Double, centerDistance: Double): Double {
        require(radiusA > 0 && radiusB > 0 && centerDistance >= 0)
        if (centerDistance >= radiusA + radiusB) return 0.0
        if (centerDistance <= kotlin.math.abs(radiusA - radiusB)) {
            return PI * min(radiusA, radiusB).pow(2)
        }
        val a = radiusA.pow(2) * acos(
            ((centerDistance.pow(2) + radiusA.pow(2) - radiusB.pow(2)) / (2 * centerDistance * radiusA))
                .coerceIn(-1.0, 1.0),
        )
        val b = radiusB.pow(2) * acos(
            ((centerDistance.pow(2) + radiusB.pow(2) - radiusA.pow(2)) / (2 * centerDistance * radiusB))
                .coerceIn(-1.0, 1.0),
        )
        val lens = 0.5 * sqrt(
            max(0.0, (-centerDistance + radiusA + radiusB) *
                (centerDistance + radiusA - radiusB) *
                (centerDistance - radiusA + radiusB) *
                (centerDistance + radiusA + radiusB)),
        )
        return a + b - lens
    }

    fun directionalOverlap(sourceRadius: Double, targetRadius: Double, centerDistance: Double): Double =
        circleIntersectionArea(sourceRadius, targetRadius, centerDistance) / (PI * sourceRadius.pow(2))

    fun chooseMerge(source: LoungeDisk, candidates: Collection<LoungeDisk>): LoungeMergeDecision? {
        val eligible = candidates.mapNotNull { target ->
            if (target.id == source.id) return@mapNotNull null
            val distance = LoungeGeometry.distanceMeters(
                source.latitude, source.longitude, target.latitude, target.longitude,
            )
            val sourceRatio = directionalOverlap(
                source.radiusMeters.toDouble(), target.radiusMeters.toDouble(), distance,
            )
            if (sourceRatio + 1e-10 < OVERLAP_THRESHOLD) return@mapNotNull null
            val targetRatio = directionalOverlap(
                target.radiusMeters.toDouble(), source.radiusMeters.toDouble(), distance,
            )
            val mutual = targetRatio + 1e-10 >= OVERLAP_THRESHOLD
            if (mutual && compareSurvivor(source, target) <= 0) return@mapNotNull null
            LoungeMergeDecision(source.id, target.id, sourceRatio)
        }
        return eligible.maxWithOrNull(
            compareBy<LoungeMergeDecision> { it.overlapRatio }
                .thenByDescending { decision -> candidates.first { it.id == decision.targetId }.createdAt }
                .thenByDescending { it.targetId.toString() },
        )
    }

    /** Negative means a survives, positive means b survives. */
    private fun compareSurvivor(a: LoungeDisk, b: LoungeDisk): Int {
        val time = a.createdAt.compareTo(b.createdAt)
        if (time != 0) return time
        return a.id.toString().compareTo(b.id.toString())
    }
}
