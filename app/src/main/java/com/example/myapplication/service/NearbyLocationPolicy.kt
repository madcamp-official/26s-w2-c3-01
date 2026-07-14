package com.example.myapplication.service

data class NearbyLocationRequestProfile(
    val intervalMillis: Long,
    val minIntervalMillis: Long,
    val minDistanceMeters: Float,
)

object NearbyLocationPolicy {
    val INTERACTIVE = NearbyLocationRequestProfile(
        intervalMillis = 5_000L,
        minIntervalMillis = 3_000L,
        minDistanceMeters = 3f,
    )

    val EFFICIENT = NearbyLocationRequestProfile(
        intervalMillis = 30_000L,
        minIntervalMillis = 15_000L,
        minDistanceMeters = 10f,
    )

    const val SAMPLE_SELECTION_WINDOW_MILLIS = 250L
    const val MAX_ACCURACY_METERS = 20f
    const val MAX_AGE_MILLIS = 15_000L
    const val INITIAL_MAX_ACCURACY_METERS = 50f
    const val INITIAL_MAX_AGE_MILLIS = 30_000L

    fun isUsable(
        observedAtMillis: Long,
        accuracyMeters: Float?,
        nowMillis: Long,
    ): Boolean {
        if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters > MAX_ACCURACY_METERS) {
            return false
        }
        val ageMillis = nowMillis - observedAtMillis
        return observedAtMillis > 0L && ageMillis in -5_000L..MAX_AGE_MILLIS
    }

    /**
     * Lets discovery start from a recent network/Wi-Fi fix while GPS is still converging.
     * Subsequent foreground updates continue to use [isUsable] and replace this coarse fix.
     */
    fun isUsableForInitialDiscovery(
        observedAtMillis: Long,
        accuracyMeters: Float?,
        nowMillis: Long,
    ): Boolean {
        if (accuracyMeters == null || !accuracyMeters.isFinite() ||
            accuracyMeters > INITIAL_MAX_ACCURACY_METERS
        ) {
            return false
        }
        val ageMillis = nowMillis - observedAtMillis
        return observedAtMillis > 0L && ageMillis in -5_000L..INITIAL_MAX_AGE_MILLIS
    }
}
