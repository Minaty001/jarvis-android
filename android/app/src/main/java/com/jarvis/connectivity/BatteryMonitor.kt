package com.jarvis.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BatteryStatus(
    val level: Int,
    val scale: Int,
    val percentage: Float,
    val isCharging: Boolean,
    val chargingSource: String,
    val health: String,
    val temperature: Float,
    val voltage: Int,
    val technology: String
)

class BatteryMonitor(private val context: Context) {
    companion object {
        private const val TAG = "BatteryMonitor"

        private fun chargingSource(plugged: Int): String = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

        private fun healthString(health: Int): String = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }

    private val _batteryStatus = MutableStateFlow<BatteryStatus?>(null)
    val batteryStatus: StateFlow<BatteryStatus?> = _batteryStatus

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) { updateBatteryStatus() }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        context.registerReceiver(receiver, filter)
        registered = true
        updateBatteryStatus()
    }

    fun unregister() {
        if (registered) { context.unregisterReceiver(receiver); registered = false }
    }

    private fun updateBatteryStatus() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percentage = if (level >= 0 && scale > 0) (level.toFloat() / scale * 100) else -1f

        _batteryStatus.value = BatteryStatus(level, scale, percentage, isCharging, chargingSource(plugged), healthString(health), temperature, voltage, technology)
    }

    fun getBatterySummary(): String {
        val status = _batteryStatus.value ?: return "Battery status unavailable"
        return buildString {
            append("Battery at ${status.percentage.toInt()} percent")
            if (status.isCharging) append(", charging via ${status.chargingSource}")
            else append(", not charging")
            append(". Temperature is ${status.temperature}C, health is ${status.health}")
        }
    }
}
