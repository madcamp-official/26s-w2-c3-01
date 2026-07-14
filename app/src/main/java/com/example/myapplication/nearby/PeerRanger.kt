package com.example.myapplication.nearby

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.gms.nearby.connection.ConnectionsClient
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
            if (PlatformRangingPolicy.enabled && Build.VERSION.SDK_INT >= 36) {
                add(PeerRangingTechnology.BLUETOOTH_CHANNEL_SOUNDING)
            }
            add(PeerRangingTechnology.BLUETOOTH_RSSI)
        }
        add(PeerRangingTechnology.GPS)
    }
}

/**
 * Android 16's OOB ranging service can crash system_server while a session is stopping.
 * Keep the platform path disabled until the framework fix is available; Nearby Connections,
 * BLE RSSI, and GPS continue to provide discovery and proximity measurements.
 */
internal object PlatformRangingPolicy {
    const val enabled = false
}

interface PlatformConnectedRanger {
    val measurements: StateFlow<Map<String, PeerProximityMeasurement>>
    fun connect(
        localBeaconId: String,
        remoteBeaconId: String,
        endpointId: String,
        initiator: Boolean,
        client: ConnectionsClient,
    )
    fun receive(endpointId: String, payload: ByteArray)
    fun disconnect(endpointId: String)
    fun stop()
}

object PlatformConnectedRangerFactory {
    fun create(context: Context): PlatformConnectedRanger =
        if (PlatformRangingPolicy.enabled && Build.VERSION.SDK_INT >= 36) {
            Api36.create(context)
        } else {
            NoOpPlatformConnectedRanger()
        }

    @RequiresApi(36)
    private object Api36 {
        fun create(context: Context): PlatformConnectedRanger = AndroidOobPeerRanger(context)
    }
}

private class NoOpPlatformConnectedRanger : PlatformConnectedRanger {
    override val measurements = kotlinx.coroutines.flow.MutableStateFlow<
        Map<String, PeerProximityMeasurement>
    >(emptyMap())
    override fun connect(
        localBeaconId: String,
        remoteBeaconId: String,
        endpointId: String,
        initiator: Boolean,
        client: ConnectionsClient,
    ) = Unit
    override fun receive(endpointId: String, payload: ByteArray) = Unit
    override fun disconnect(endpointId: String) = Unit
    override fun stop() = Unit
}
