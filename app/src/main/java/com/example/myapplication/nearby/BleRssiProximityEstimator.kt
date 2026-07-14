package com.example.myapplication.nearby

import com.example.myapplication.core.model.NearbyMeasurementMethod
import com.example.myapplication.core.model.NearbyProximityConfidence
import com.example.myapplication.core.model.Proximity
import kotlin.math.pow

class BleRssiProximityEstimator(
    private val measuredPowerAtOneMeter: Int = -59,
    private val pathLossExponent: Double = 2.2,
) {
    private val readingsByBeacon = mutableMapOf<String, ArrayDeque<Int>>()

    @Synchronized
    fun add(beaconId: String, rssi: Int, observedAtEpochMillis: Long): PeerProximityMeasurement? {
        if (rssi !in MIN_RSSI..MAX_RSSI) return null
        val readings = readingsByBeacon.getOrPut(beaconId) { ArrayDeque() }
        readings.addLast(rssi)
        while (readings.size > WINDOW_SIZE) readings.removeFirst()
        if (readings.size < MIN_SAMPLES) return null

        val sorted = readings.sorted()
        val medianRssi = sorted[sorted.size / 2]
        val distanceMeters = 10.0.pow(
            (measuredPowerAtOneMeter - medianRssi) / (10.0 * pathLossExponent)
        )
        val proximity = when {
            distanceMeters <= 10.0 -> Proximity.WITHIN_10M
            distanceMeters <= 20.0 -> Proximity.WITHIN_20M
            else -> return null
        }
        val spread = (sorted.last() - sorted.first()).toDouble()
        val boundaryGap = minOf(
            kotlin.math.abs(distanceMeters - 10.0),
            kotlin.math.abs(distanceMeters - 20.0),
        )
        val confidence = if (spread <= MAX_HIGH_CONFIDENCE_SPREAD_DB && boundaryGap >= 1.0) {
            NearbyProximityConfidence.HIGH
        } else {
            NearbyProximityConfidence.LOW
        }
        return PeerProximityMeasurement(
            beaconId = beaconId,
            proximity = proximity,
            confidence = confidence,
            method = NearbyMeasurementMethod.BLUETOOTH,
            observedAtEpochMillis = observedAtEpochMillis,
        )
    }

    @Synchronized
    fun clear() = readingsByBeacon.clear()

    private companion object {
        const val WINDOW_SIZE = 7
        const val MIN_SAMPLES = 5
        const val MIN_RSSI = -127
        const val MAX_RSSI = -20
        const val MAX_HIGH_CONFIDENCE_SPREAD_DB = 6.0
    }
}
