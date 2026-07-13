package com.example.myapplication.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.security.MessageDigest

/** Returns the current SSID and a one-way identifier shared by that SSID. */
data class WifiIdentity(val displayName: String, val fingerprint: String)

sealed interface WifiIdentityResult {
    data class Available(val identity: WifiIdentity) : WifiIdentityResult
    data object NotConnected : WifiIdentityResult
    data object SsidUnavailable : WifiIdentityResult
}

object WifiFingerprintProvider {
    fun current(context: Context): WifiIdentity? =
        (currentResult(context) as? WifiIdentityResult.Available)?.identity

    fun currentResult(context: Context): WifiIdentityResult {
        val modernWifiInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                val network = connectivity.activeNetwork ?: return WifiIdentityResult.NotConnected
                val capabilities = connectivity.getNetworkCapabilities(network)
                    ?: return WifiIdentityResult.NotConnected
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return WifiIdentityResult.NotConnected
                }
                capabilities.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .connectionInfo
            }
        }.getOrNull()
        val modernSsid = modernWifiInfo?.usableSsid()

        // Some Android 12+ devices redact the on-demand NetworkCapabilities WifiInfo even when
        // fine location is granted. The legacy accessor remains a useful compatibility fallback
        // for the currently connected network and is still permission-gated by Android.
        @Suppress("DEPRECATION")
        val fallbackSsid = if (modernSsid == null) {
            runCatching {
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .connectionInfo
                    .usableSsid()
            }.getOrNull()
        } else null
        val displayName = (modernSsid ?: fallbackSsid)
            ?.take(80)
            ?: return WifiIdentityResult.SsidUnavailable

        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(displayName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return WifiIdentityResult.Available(WifiIdentity(displayName, fingerprint))
    }

    private fun WifiInfo.usableSsid(): String? = ssid
            ?.removeSurrounding("\"")
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("<unknown ssid>", ignoreCase = true) }
}
