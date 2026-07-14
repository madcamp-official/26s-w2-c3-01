package com.example.myapplication.nearby
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
/** Passive discovery only: endpoint names contain short-lived, server-issued opaque beacon IDs. */
class PassiveNearbyDiscoveryManager(context: Context) {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId = "com.example.myapplication.NEARBY_DISCOVERY_V1"
    private val endpoints = linkedMapOf<String, String>()
    private val handler = Handler(Looper.getMainLooper())
    private val pendingLosses = mutableMapOf<String, Runnable>()
    private val _beaconIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconIds: StateFlow<Set<String>> = _beaconIds.asStateFlow()
    private var running = false
    @SuppressLint("MissingPermission")
    fun start(localBeaconId: String) {
        stop()
        running = true
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
        runCatching { client.stopAdvertising() }
        startAdvertising(localBeaconId)
    }
    fun stop() {
        running = false
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
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
        }
        override fun onEndpointLost(endpointId: String) {
            pendingLosses.remove(endpointId)?.let(handler::removeCallbacks)
            val removal = Runnable {
                pendingLosses.remove(endpointId)
                endpoints.remove(endpointId)
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
            client.rejectConnection(endpointId)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) = Unit
        override fun onDisconnected(endpointId: String) = Unit
    }
    private companion object {
        val BEACON_PATTERN = Regex("mb1_[a-f0-9]{32}")
        const val ENDPOINT_LOSS_GRACE_MILLIS = 10_000L
    }
}
