package com.example.myapplication.service

data class NearbyLocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val observedAtEpochMillis: Long,
    val elapsedRealtimeNanos: Long,
    val source: String,
)

class AccuracyFirstLocationSelector(
    private val freshnessPenaltyPerMillisecond: Float = 0.01f,
) {
    private val pending = ArrayDeque<NearbyLocationSample>()
    private var lastConsumedElapsedRealtimeNanos = Long.MIN_VALUE

    @Synchronized
    fun offer(sample: NearbyLocationSample) {
        if (sample.elapsedRealtimeNanos <= lastConsumedElapsedRealtimeNanos) return
        if (!sample.accuracyMeters.isFinite() || sample.accuracyMeters < 0f) return
        pending.addLast(sample)
        while (pending.size > MAX_PENDING_SAMPLES) pending.removeFirst()
    }

    @Synchronized
    fun takeBest(nowElapsedRealtimeNanos: Long): NearbyLocationSample? {
        if (pending.isEmpty()) return null
        val samples = pending.toList()
        pending.clear()
        lastConsumedElapsedRealtimeNanos = maxOf(
            lastConsumedElapsedRealtimeNanos,
            samples.maxOf(NearbyLocationSample::elapsedRealtimeNanos),
        )
        return samples.minBy { sample ->
            val ageMillis = ((nowElapsedRealtimeNanos - sample.elapsedRealtimeNanos) / 1_000_000L)
                .coerceAtLeast(0L)
            sample.accuracyMeters + ageMillis * freshnessPenaltyPerMillisecond
        }
    }

    private companion object {
        const val MAX_PENDING_SAMPLES = 64
    }
}
