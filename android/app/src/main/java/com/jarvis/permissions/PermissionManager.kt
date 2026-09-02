package com.jarvis.permissions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionManager {

    const val AUDIO = Manifest.permission.RECORD_AUDIO
    const val CONTACTS = Manifest.permission.READ_CONTACTS
    const val CALL_PHONE = Manifest.permission.CALL_PHONE
    const val SMS = Manifest.permission.SEND_SMS
    const val CALENDAR = Manifest.permission.READ_CALENDAR
    const val BT_CONNECT = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
    const val BT_SCAN = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else null

    fun isGranted(activity: Activity, permission: String): Boolean {
        if (permission.isBlank()) return true
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestAudio(activity: Activity, requestCode: Int = 1001) {
        if (!isGranted(activity, AUDIO)) {
            ActivityCompat.requestPermissions(activity, arrayOf(AUDIO), requestCode)
        }
    }

    fun requestContacts(activity: Activity, requestCode: Int = 1002) {
        if (!isGranted(activity, CONTACTS)) {
            ActivityCompat.requestPermissions(activity, arrayOf(CONTACTS), requestCode)
        }
    }

    fun requestCallPhone(activity: Activity, requestCode: Int = 1003) {
        if (!isGranted(activity, CALL_PHONE)) {
            ActivityCompat.requestPermissions(activity, arrayOf(CALL_PHONE), requestCode)
        }
    }

    fun requestSms(activity: Activity, requestCode: Int = 1004) {
        if (!isGranted(activity, SMS)) {
            ActivityCompat.requestPermissions(activity, arrayOf(SMS), requestCode)
        }
    }

    fun requestCalendar(activity: Activity, requestCode: Int = 1005) {
        if (!isGranted(activity, CALENDAR)) {
            ActivityCompat.requestPermissions(activity, arrayOf(CALENDAR), requestCode)
        }
    }

    fun requestBluetooth(activity: Activity, requestCode: Int = 1006) {
        val perms = mutableListOf<String>()
        BT_CONNECT?.let { if (!isGranted(activity, it)) perms.add(it) }
        BT_SCAN?.let { if (!isGranted(activity, it)) perms.add(it) }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, perms.toTypedArray(), requestCode)
        }
    }
}
