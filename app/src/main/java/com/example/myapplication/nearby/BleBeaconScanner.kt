package com.example.myapplication.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleBeaconScanner(context: Context) {
    private val bluetoothManager = context.applicationContext
        .getSystemService(BluetoothManager::class.java)
    private val scanner
        get() = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
    private val estimator = BleRssiProximityEstimator()
    private val handler = Handler(Looper.getMainLooper())
    private val _measurements = MutableStateFlow<Map<String, PeerProximityMeasurement>>(emptyMap())
    val measurements: StateFlow<Map<String, PeerProximityMeasurement>> = _measurements.asStateFlow()
    private var localBeaconId: String? = null
    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = receive(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::receive)
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            Log.w("MelodyRanging", "BLE ranging scan failed code=$errorCode")
        }
    }

    private val pruneRunnable = object : Runnable {
        override fun run() {
            val cutoff = System.currentTimeMillis() - MEASUREMENT_TTL_MILLIS
            _measurements.value = _measurements.value.filterValues {
                it.observedAtEpochMillis >= cutoff
            }
            if (scanning) handler.postDelayed(this, PRUNE_INTERVAL_MILLIS)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(localBeaconId: String) {
        this.localBeaconId = localBeaconId
        if (scanning) return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()
        runCatching { scanner?.startScan(null, settings, callback) }
            .onSuccess {
                scanning = scanner != null
                if (scanning) handler.post(pruneRunnable)
            }
            .onFailure { Log.w("MelodyRanging", "BLE ranging scan unavailable", it) }
    }

    fun rotate(localBeaconId: String) {
        this.localBeaconId = localBeaconId
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (scanning) runCatching { scanner?.stopScan(callback) }
        scanning = false
        handler.removeCallbacks(pruneRunnable)
        estimator.clear()
        _measurements.value = emptyMap()
    }

    private fun receive(result: ScanResult) {
        val payload = result.scanRecord
            ?.getManufacturerSpecificData(BleBeaconCodec.MANUFACTURER_ID)
        val beaconId = BleBeaconCodec.decode(payload) ?: return
        if (beaconId == localBeaconId) return
        val measurement = estimator.add(beaconId, result.rssi, System.currentTimeMillis()) ?: return
        _measurements.value = _measurements.value + (beaconId to measurement)
    }

    private companion object {
        // Android may briefly pause BLE callbacks during radio arbitration or screen-state
        // changes. Keep the latest stable band long enough to bridge that gap without making it
        // look like a peer vanished and immediately reappeared.
        const val MEASUREMENT_TTL_MILLIS = 10_000L
        const val PRUNE_INTERVAL_MILLIS = 1_000L
    }
}
