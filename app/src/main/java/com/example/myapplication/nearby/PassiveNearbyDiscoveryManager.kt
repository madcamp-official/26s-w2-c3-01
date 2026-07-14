package com.example.myapplication.nearby
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
/** Passive discovery only: endpoint names contain short-lived, server-issued opaque beacon IDs. */
class PassiveNearbyDiscoveryManager(context: Context) {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val peerRanger: PeerRanger = BlePeerRanger(context.applicationContext)
    private val platformRanger = PlatformConnectedRangerFactory.create(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val serviceId = "com.example.myapplication.NEARBY_DISCOVERY_V1"
    private val endpoints = linkedMapOf<String, String>()
    private val handler = Handler(Looper.getMainLooper())
    private val pendingLosses = mutableMapOf<String, Runnable>()
    private val _beaconIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconIds: StateFlow<Set<String>> = _beaconIds.asStateFlow()
    val proximityMeasurements: StateFlow<Map<String, PeerProximityMeasurement>> = combine(
        peerRanger.measurements,
        platformRanger.measurements,
    ) { bluetooth, platform -> bluetooth + platform }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
    private var running = false
    private var localBeaconId: String? = null
    @SuppressLint("MissingPermission")
    fun start(localBeaconId: String) {
        stop()
        running = true
        this.localBeaconId = localBeaconId
        peerRanger.start(localBeaconId)
        startAdvertising(localBeaconId)
        runCatching {
            client.startDiscovery(
                serviceId,
                discoveryCallback,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
            ).addOnFailureListener { stop() }
        }.onFailure { stop() }
    }
    @SuppressLint("MissingPermission")
    fun rotate(localBeaconId: String) {
        if (!running) return
        this.localBeaconId = localBeaconId
        peerRanger.rotate(localBeaconId)
        runCatching { client.stopAdvertising() }
        startAdvertising(localBeaconId)
    }
    fun stop() {
        running = false
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        peerRanger.stop()
        platformRanger.stop()
        localBeaconId = null
        endpoints.clear()
        pendingLosses.values.forEach(handler::removeCallbacks)
        pendingLosses.clear()
        _beaconIds.value = emptySet()
    }
    @SuppressLint("MissingPermission")
    private fun startAdvertising(beaconId: String) {
        runCatching {
            client.startAdvertising(
                beaconId.take(40),
                serviceId,
                connectionCallback,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
            ).addOnFailureListener { /* GPS/PostGIS remains the fallback. */ }
        }
    }
    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val beaconId = info.endpointName.takeIf { it.matches(BEACON_PATTERN) } ?: return
            pendingLosses.remove(endpointId)?.let(handler::removeCallbacks)
            endpoints[endpointId] = beaconId
            _beaconIds.value = endpoints.values.toSet()
            requestPlatformConnection(endpointId, beaconId)
        }
        override fun onEndpointLost(endpointId: String) {
            pendingLosses.remove(endpointId)?.let(handler::removeCallbacks)
            val removal = Runnable {
                pendingLosses.remove(endpointId)
                endpoints.remove(endpointId)
                platformRanger.disconnect(endpointId)
                _beaconIds.value = endpoints.values.toSet()
            }
            pendingLosses[endpointId] = removal
            handler.postDelayed(removal, ENDPOINT_LOSS_GRACE_MILLIS)
        }
    }
    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(
            endpointId: String,
            info: com.google.android.gms.nearby.connection.ConnectionInfo,
        ) {
            val remoteBeaconId = info.endpointName.takeIf { it.matches(BEACON_PATTERN) }
            if (Build.VERSION.SDK_INT < 36 || remoteBeaconId == null) {
                client.rejectConnection(endpointId)
                return
            }
            endpoints[endpointId] = remoteBeaconId
            client.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val local = localBeaconId ?: return
            val remote = endpoints[endpointId] ?: return
            if (result.status.isSuccess) {
                platformRanger.connect(local, remote, endpointId, local < remote, client)
            }
        }
        override fun onDisconnected(endpointId: String) = platformRanger.disconnect(endpointId)
    }
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { platformRanger.receive(endpointId, it) }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }
    @SuppressLint("MissingPermission")
    private fun requestPlatformConnection(endpointId: String, remoteBeaconId: String) {
        val local = localBeaconId ?: return
        if (Build.VERSION.SDK_INT < 36 || local >= remoteBeaconId) return
        client.requestConnection(local, endpointId, connectionCallback)
    }
    private companion object {
        val BEACON_PATTERN = Regex("mb1_[a-f0-9]{32}")
        const val ENDPOINT_LOSS_GRACE_MILLIS = 10_000L
    }
}
