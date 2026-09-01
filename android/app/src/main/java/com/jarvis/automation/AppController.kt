package com.jarvis.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

data class AppInfo(
    val packageName: String,
    val label: String
)

class AppController(private val context: Context) {
    companion object {
        private const val TAG = "AppController"
    }

    var actionRecorder: ActionRecorder? = null

    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            actionRecorder?.recordAppLaunch(packageName)
            Log.d(TAG, "Launched: $packageName")
            return true
        }
        Log.w(TAG, "Cannot launch: $packageName")
        return false
    }

    fun launchAppByLabel(label: String): Boolean {
        val apps = getInstalledApps()
        val app = apps.find { it.label.equals(label, ignoreCase = true) }
        return if (app != null) {
            launchApp(app.packageName)
        } else {
            Log.w(TAG, "App not found: $label")
            false
        }
    }

    fun closeApp(packageName: String): Boolean {
        val autoService = JarvisAccessibilityService.instance ?: return false

        autoService.pressRecents()
        Thread.sleep(500)

        val nodes = autoService.findByText(getAppLabel(packageName))
        if (nodes.isNotEmpty()) {
            val (x, y) = autoService.getNodeCenter(nodes[0])
            autoService.swipe(x, y, x - 300, y, 200)
            nodes.forEach { it.recycle() }
            Log.d(TAG, "Closed: $packageName")
            return true
        }
        autoService.pressHome()
        return false
    }

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.MATCH_ALL)
        }

        return packages
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { pkg ->
                val label = pm.getApplicationLabel(pkg.applicationInfo).toString()
                AppInfo(pkg.packageName, label)
            }
            .sortedBy { it.label.lowercase() }
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun findApp(query: String): List<AppInfo> {
        return getInstalledApps().filter {
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }
}
