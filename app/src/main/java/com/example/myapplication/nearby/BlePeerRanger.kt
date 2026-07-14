package com.example.myapplication.nearby

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

class BlePeerRanger(context: Context) : PeerRanger {
    private val advertiser = BleBeaconAdvertiser(context)
    private val scanner = BleBeaconScanner(context)

    override val measurements: StateFlow<Map<String, PeerProximityMeasurement>> =
        scanner.measurements

    override fun start(localBeaconId: String) {
        advertiser.start(localBeaconId)
        scanner.start(localBeaconId)
    }

    override fun rotate(localBeaconId: String) {
        advertiser.rotate(localBeaconId)
        scanner.rotate(localBeaconId)
    }

    override fun stop() {
        advertiser.stop()
        scanner.stop()
    }
}
