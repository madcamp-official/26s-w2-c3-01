package com.example.myapplication.nearby

import android.ranging.RangingData
import android.ranging.RangingManager
import android.ranging.RangingMeasurement
import androidx.annotation.RequiresApi
import com.example.myapplication.core.model.NearbyMeasurementMethod
import com.example.myapplication.core.model.NearbyProximityConfidence
import com.example.myapplication.core.model.Proximity

@RequiresApi(36)
object AndroidRangingMapper {
    fun toMeasurement(
        beaconId: String,
        data: RangingData,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): PeerProximityMeasurement? {
        val distanceMeasurement = data.distance ?: return null
        val distance = distanceMeasurement.measurement
        val proximity = when {
            distance <= 5.0 -> Proximity.WITHIN_5M
            distance <= 10.0 -> Proximity.WITHIN_10M
            distance <= 15.0 -> Proximity.WITHIN_15M
            else -> return null
        }
        val confidence = if (
            distanceMeasurement.confidence == RangingMeasurement.CONFIDENCE_HIGH
        ) NearbyProximityConfidence.HIGH else NearbyProximityConfidence.LOW
        val method = when (data.rangingTechnology) {
            RangingManager.UWB -> NearbyMeasurementMethod.UWB
            RangingManager.WIFI_NAN_RTT -> NearbyMeasurementMethod.WIFI_RTT
            RangingManager.BLE_CS,
            RangingManager.BLE_RSSI,
            -> NearbyMeasurementMethod.BLUETOOTH
            else -> NearbyMeasurementMethod.UNKNOWN
        }
        return PeerProximityMeasurement(
            beaconId = beaconId,
            proximity = proximity,
            confidence = confidence,
            method = method,
            observedAtEpochMillis = nowEpochMillis,
        )
    }
}
