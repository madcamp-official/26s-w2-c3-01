package com.example.myapplication.nearby

import android.content.Context
import android.ranging.RangingConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import android.ranging.oob.DeviceHandle
import android.ranging.oob.OobInitiatorRangingConfig
import android.ranging.oob.OobResponderRangingConfig
import androidx.annotation.RequiresApi
import com.google.android.gms.nearby.connection.ConnectionsClient
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@RequiresApi(36)
class AndroidOobPeerRanger(context: Context) : PlatformConnectedRanger {
    private data class Active(
        val beaconId: String,
        val transport: NearbyTransportHandle,
        val session: RangingSession,
    )

    private val manager = context.getSystemService(RangingManager::class.java)
    private val executor = context.mainExecutor
    private val activeByEndpoint = mutableMapOf<String, Active>()
    private val _measurements = MutableStateFlow<Map<String, PeerProximityMeasurement>>(emptyMap())
    override val measurements = _measurements.asStateFlow()

    override fun connect(
        localBeaconId: String,
        remoteBeaconId: String,
        endpointId: String,
        initiator: Boolean,
        client: ConnectionsClient,
    ) {
        if (endpointId in activeByEndpoint) return
        val transport = NearbyTransportHandle(client, endpointId)
        val device = RangingDevice.Builder()
            .setUuid(UUID.nameUUIDFromBytes(remoteBeaconId.toByteArray(StandardCharsets.UTF_8)))
            .build()
        val handle = DeviceHandle.Builder(device, transport).build()
        val config: RangingConfig = if (initiator) {
            OobInitiatorRangingConfig.Builder()
                .addDeviceHandle(handle)
                .setRangingMode(OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY_PREFERRED)
                .setSecurityLevel(OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE)
                .setFastestRangingInterval(Duration.ofMillis(100L))
                .setSlowestRangingInterval(Duration.ofMillis(500L))
                .build()
        } else {
            OobResponderRangingConfig.Builder(handle).build()
        }
        val session = manager?.createRangingSession(executor, callback(endpointId, remoteBeaconId)) ?: return
        activeByEndpoint[endpointId] = Active(remoteBeaconId, transport, session)
        val role = if (initiator) {
            RangingPreference.DEVICE_ROLE_INITIATOR
        } else RangingPreference.DEVICE_ROLE_RESPONDER
        runCatching { session.start(RangingPreference.Builder(role, config).build()) }
            .onFailure { disconnect(endpointId) }
    }

    override fun receive(endpointId: String, payload: ByteArray) {
        activeByEndpoint[endpointId]?.transport?.receive(payload)
    }

    override fun disconnect(endpointId: String) {
        val active = activeByEndpoint.remove(endpointId) ?: return
        runCatching { active.session.stop() }
        runCatching { active.session.close() }
        active.transport.disconnected()
        active.transport.close()
        _measurements.value = _measurements.value - active.beaconId
    }

    override fun stop() = activeByEndpoint.keys.toList().forEach(::disconnect)

    private fun callback(endpointId: String, beaconId: String) = object : RangingSession.Callback {
        override fun onResults(device: RangingDevice, data: RangingData) {
            val measurement = AndroidRangingMapper.toMeasurement(beaconId, data) ?: return
            _measurements.value = _measurements.value + (beaconId to measurement)
        }
        override fun onOpenFailed(reason: Int) = disconnect(endpointId)
        override fun onClosed(reason: Int) = disconnect(endpointId)
        override fun onOpened() = Unit
        override fun onStarted(device: RangingDevice, technology: Int) = Unit
        override fun onStopped(device: RangingDevice, reason: Int) = Unit
    }
}
