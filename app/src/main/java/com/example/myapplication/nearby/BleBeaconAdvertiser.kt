package com.example.myapplication.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log

class BleBeaconAdvertiser(context: Context) {
    private val bluetoothManager = context.applicationContext
        .getSystemService(BluetoothManager::class.java)
    private val advertiser
        get() = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser
    private var advertising = false

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            Log.w("MelodyRanging", "BLE ranging advertisement failed code=$errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun start(beaconId: String) {
        val payload = BleBeaconCodec.encode(beaconId) ?: return
        stop()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(true)
            .addManufacturerData(BleBeaconCodec.MANUFACTURER_ID, payload)
            .build()
        runCatching { advertiser?.startAdvertising(settings, data, callback) }
            .onFailure { Log.w("MelodyRanging", "BLE ranging advertisement unavailable", it) }
    }

    fun rotate(beaconId: String) = start(beaconId)

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!advertising) return
        runCatching { advertiser?.stopAdvertising(callback) }
        advertising = false
    }
}
