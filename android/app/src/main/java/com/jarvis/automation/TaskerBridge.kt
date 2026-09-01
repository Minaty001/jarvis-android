package com.jarvis.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class TaskerBridge(private val context: Context) {
    companion object {
        private const val TAG = "TaskerBridge"
        private const val PERMISSION_SEND_COMMAND = "net.dinglisch.android.tasker.PERMISSION_SEND_COMMAND"
    }

    val isInstalled: Boolean
        get() = try {
            context.packageManager.getPackageInfo("net.dinglisch.android.taskerm", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    val hasPermission: Boolean
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true
                else context.checkSelfPermission(PERMISSION_SEND_COMMAND) == PackageManager.PERMISSION_GRANTED

    fun sendCommand(command: String): Boolean {
        if (!isInstalled) {
            Log.w(TAG, "Tasker not installed")
            return false
        }

        return try {
            val intent = Intent().apply {
                setClassName(
                    "net.dinglisch.android.taskerm",
                    "com.joaomgcd.taskerm.command.ServiceSendCommand"
                )
                putExtra("command", command)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                context.startService(intent)
            } else {
                context.startForegroundService(intent)
            }
            Log.d(TAG, "Tasker command sent: $command")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Tasker command", e)
            false
        }
    }

    fun launchAppViaTasker(packageName: String): Boolean {
        return sendCommand("launchapp=:= $packageName")
    }

    fun toggleWiFiViaTasker(enable: Boolean): Boolean {
        val state = if (enable) "on" else "off"
        return sendCommand("wifitoggle=:= $state")
    }

    fun toggleBluetoothViaTasker(enable: Boolean): Boolean {
        val state = if (enable) "on" else "off"
        return sendCommand("bttoggle=:= $state")
    }

    fun setVolumeViaTasker(level: Int): Boolean {
        return sendCommand("volume=:= $level")
    }

    fun runTask(taskName: String): Boolean {
        return sendCommand("runtask=:= $taskName")
    }
}
