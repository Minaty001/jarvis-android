package com.jarvis.automation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.jarvis.calendar.CalendarManager
import com.jarvis.connectivity.BatteryMonitor
import com.jarvis.connectivity.BluetoothController
import com.jarvis.connectivity.WifiController
import com.jarvis.messaging.SmsController
import com.jarvis.sharing.ShareManager
import kotlinx.coroutines.runBlocking

class AutomationController(val context: Context) {
    companion object {
        private const val TAG = "AutomationController"
    }

    val appController = AppController(context)
    val smsController = SmsController(context)
    val bluetoothController = BluetoothController(context)
    val wifiController = WifiController(context)
    val batteryMonitor = BatteryMonitor(context)
    val calendarManager = CalendarManager(context)
    val shareManager = ShareManager(context)

    val isAccessibilityEnabled: Boolean
        get() {
            val serviceName = "${context.packageName}/com.jarvis.automation.JarvisAccessibilityService"
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
        }

    val isTaskerAvailable: Boolean get() = appController.isAppInstalled("net.dinglisch.android.taskerm")

    fun openAccessibilitySettings() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    }

    // ==================== HIGH-LEVEL AUTOMATION ====================

    fun openApp(appName: String): Boolean = appController.launchAppByLabel(appName)
    fun tapElement(text: String): Boolean = JarvisAccessibilityService.instance?.clickByText(text) ?: false
    fun readScreen(): String = JarvisAccessibilityService.instance?.getScreenContent() ?: ""
    fun typeText(text: String): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        val nodes = service.findByViewId("android:id/edit")
        if (nodes.isNotEmpty()) { val r = service.setText(nodes[0], text); nodes.forEach { it.recycle() }; return r }
        return false
    }
    fun goBack(): Boolean = JarvisAccessibilityService.instance?.pressBack() ?: false
    fun goHome(): Boolean = JarvisAccessibilityService.instance?.pressHome() ?: false

    // ==================== SMS ====================

    fun sendSms(phone: String, message: String) = smsController.sendSms(phone, message)

    // ==================== BLUETOOTH ====================

    fun toggleBluetooth(enable: Boolean): Boolean { bluetoothController.register(); return bluetoothController.setEnabled(enable) }
    fun toggleBluetoothAuto(): Boolean { bluetoothController.register(); return bluetoothController.toggle() }
    fun getBondedDevices() = bluetoothController.getBondedDevices()

    // ==================== WIFI ====================

    fun toggleWifi(enable: Boolean): Boolean = wifiController.setEnabled(enable)
    fun toggleWifiAuto(): Boolean = wifiController.toggle()
    fun getWifiInfo() = wifiController.getConnectionInfo()

    // ==================== BATTERY ====================

    fun getBatterySummary(): String { batteryMonitor.register(); return batteryMonitor.getBatterySummary() }
    fun getBatteryStatus() = batteryMonitor.batteryStatus.value

    // ==================== CALENDAR ====================

    fun getTodayEvents() = runBlocking { calendarManager.getTodayEvents() }
    fun searchCalendar(query: String) = runBlocking { calendarManager.searchEvents(query) }
    fun getCalendarSummary() = runBlocking { calendarManager.formatEventsSummary(calendarManager.getUpcomingEvents(1)) }

    // ==================== SHARING ====================

    fun shareText(text: String) = shareManager.shareText(text)
    fun shareTextToApp(text: String, packageName: String) = shareManager.shareTextToApp(text, packageName)

    // ==================== COMPLEX SEQUENCES ====================

    fun openYouTubeAndSearch(query: String): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        if (!appController.launchApp("com.google.android.youtube")) return false
        Thread.sleep(2000)
        service.clickByText("Search")
        Thread.sleep(1000)
        val nodes = service.findByViewId("com.google.android.youtube:id/search_edit_text")
        if (nodes.isNotEmpty()) { service.setText(nodes[0], query); nodes.forEach { it.recycle() }; Thread.sleep(500); service.pressBack(); Thread.sleep(1000); return true }
        return false
    }

    fun openChromeAndSearch(query: String): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        if (!appController.launchApp("com.android.chrome")) return false
        Thread.sleep(2000)
        val nodes = service.findByViewId("com.android.chrome:id/url_bar")
        if (nodes.isNotEmpty()) { service.setText(nodes[0], query); nodes.forEach { it.recycle() }; Thread.sleep(500); service.pressBack(); Thread.sleep(1000); return true }
        return false
    }

    fun sendWhatsAppMessage(contact: String, message: String): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        if (!appController.launchApp("com.whatsapp")) return false
        Thread.sleep(2000)
        service.clickByText("Search")
        Thread.sleep(1000)
        service.setTextByFind("Search", contact)
        Thread.sleep(1500)
        service.clickByText(contact)
        Thread.sleep(1000)
        val nodes = service.findByViewId("com.whatsapp:id_entry")
        if (nodes.isNotEmpty()) { service.setText(nodes[0], message); nodes.forEach { it.recycle() }; Thread.sleep(500); service.clickByText("Send"); return true }
        return false
    }
}
