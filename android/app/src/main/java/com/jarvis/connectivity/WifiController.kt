package com.jarvis.connectivity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WifiInfo(val isConnected: Boolean, val ssid: String?, val rssi: Int?, val linkSpeed: Int?)

class WifiController(private val context: Context) {
    companion object {
        private const val TAG = "WifiController"
    }

    @Suppress("DEPRECATION")
    private val wifiManager: WifiManager? = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isEnabled = MutableStateFlow(wifiManager?.isWifiEnabled == true)
    val isEnabled: StateFlow<Boolean> = _isEnabled

    @Suppress("DEPRECATION")
    fun setEnabled(enable: Boolean): Boolean {
        if (wifiManager == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openWifiSettings()
            return false
        }
        return try {
            val result = wifiManager.setWifiEnabled(enable)
            _isEnabled.value = enable
            result
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException toggling WiFi", e)
            false
        }
    }

    fun toggle(): Boolean = setEnabled(!_isEnabled.value)

    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    fun getConnectionInfo(): WifiInfo {
        if (wifiManager == null) return WifiInfo(false, null, null, null)
        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager?.activeNetwork ?: return WifiInfo(false, null, null, null)
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return WifiInfo(false, null, null, null)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION") wifiManager.isWifiEnabled
        }
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        val ssid = info?.ssid?.let { if (it == "<unknown ssid>") null else it.trim('"') }
        return WifiInfo(isConnected, ssid, info?.rssi, info?.linkSpeed)
    }

    fun refreshState() { _isEnabled.value = wifiManager?.isWifiEnabled == true }
}
