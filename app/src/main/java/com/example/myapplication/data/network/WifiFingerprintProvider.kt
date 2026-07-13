package com.example.myapplication.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.security.MessageDigest

/** Returns the current SSID and a one-way identifier shared by that SSID. */
data class WifiIdentity(val displayName: String, val fingerprint: String)

object WifiFingerprintProvider {
    fun current(context: Context): WifiIdentity? {
        val wifiInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                val network = connectivity.activeNetwork ?: return@runCatching null
                connectivity.getNetworkCapabilities(network)?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .connectionInfo
            }
        }.getOrNull() ?: return null
        val displayName = wifiInfo.ssid
            ?.removeSurrounding("\"")
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("<unknown ssid>", ignoreCase = true) }
            ?.take(80)
            ?: return null

        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(displayName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return WifiIdentity(displayName, fingerprint)
    }
}
