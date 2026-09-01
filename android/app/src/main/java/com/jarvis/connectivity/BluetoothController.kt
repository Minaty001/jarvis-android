package com.jarvis.connectivity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BluetoothDeviceInfo(val name: String, val address: String, val bondState: Int)

class BluetoothController(private val context: Context) {
    companion object {
        private const val TAG = "BluetoothController"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isEnabled: StateFlow<Boolean> = _isEnabled

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = _discoveredDevices

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isEnabled.value = (state == BluetoothAdapter.STATE_ON)
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val info = BluetoothDeviceInfo(it.name ?: "Unknown", it.address, it.bondState)
                        _discoveredDevices.value = _discoveredDevices.value + info
                    }
                }
            }
        }
    }

    private var registered = false

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun unregister() {
        if (registered) { context.unregisterReceiver(receiver); registered = false }
    }

    fun hasPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true

    fun setEnabled(enable: Boolean): Boolean {
        if (bluetoothAdapter == null || !hasPermission()) return false
        return if (enable) bluetoothAdapter.enable() else bluetoothAdapter.disable()
    }

    fun toggle(): Boolean = setEnabled(!_isEnabled.value)

    fun startDiscovery(): Boolean {
        if (bluetoothAdapter == null || !hasPermission()) return false
        _discoveredDevices.value = emptyList()
        @Suppress("DEPRECATION") return bluetoothAdapter.startDiscovery()
    }

    fun getBondedDevices(): List<BluetoothDeviceInfo> {
        if (bluetoothAdapter == null || !hasPermission()) return emptyList()
        return bluetoothAdapter.bondedDevices?.map {
            BluetoothDeviceInfo(it.name ?: "Unknown", it.address, it.bondState)
        } ?: emptyList()
    }
}
