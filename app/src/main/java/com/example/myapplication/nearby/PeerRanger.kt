package com.example.myapplication.nearby

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.myapplication.core.model.NearbyMeasurementMethod
import com.example.myapplication.core.model.NearbyProximityConfidence
import com.example.myapplication.core.model.Proximity
import kotlinx.coroutines.flow.StateFlow

data class PeerProximityMeasurement(
    val beaconId: String,
    val proximity: Proximity,
    val confidence: NearbyProximityConfidence,
    val method: NearbyMeasurementMethod,
    val observedAtEpochMillis: Long,
)

interface PeerRanger {
    val measurements: StateFlow<Map<String, PeerProximityMeasurement>>
    fun start(localBeaconId: String)
    fun rotate(localBeaconId: String)
    fun stop()
}

enum class PeerRangingTechnology {
    UWB,
    WIFI_RTT,
    BLUETOOTH_CHANNEL_SOUNDING,
    BLUETOOTH_RSSI,
    GPS,
}

class PeerRangingCapabilityDetector(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun supportedTechnologies(): Set<PeerRangingTechnology> = buildSet {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
        ) add(PeerRangingTechnology.UWB)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            add(PeerRangingTechnology.WIFI_RTT)
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (Build.VERSION.SDK_INT >= 36) {
                add(PeerRangingTechnology.BLUETOOTH_CHANNEL_SOUNDING)
            }
            add(PeerRangingTechnology.BLUETOOTH_RSSI)
        }
        add(PeerRangingTechnology.GPS)
    }
}
