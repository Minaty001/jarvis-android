package com.jarvis.messaging

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

data class SmsResult(val success: Boolean, val message: String)

class SmsController(private val context: Context) {
    companion object {
        private const val TAG = "SmsController"
    }

    fun hasPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun sendSms(phoneNumber: String, message: String): SmsResult {
        if (!hasPermission()) {
            return SmsResult(false, "SEND_SMS permission not granted")
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (message.length <= 160) {
                val sentIntent = PendingIntent.getBroadcast(
                    context, 0,
                    Intent("SMS_SENT_ACTION"),
                    PendingIntent.FLAG_IMMUTABLE
                )
                smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
                SmsResult(true, "SMS sent successfully")
            } else {
                val parts = smsManager.divideMessage(message)
                val sentIntents = ArrayList<PendingIntent>()
                parts.forEachIndexed { index, _ ->
                    sentIntents.add(PendingIntent.getBroadcast(
                        context, index,
                        Intent("SMS_SENT_ACTION"),
                        PendingIntent.FLAG_IMMUTABLE
                    ))
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
                SmsResult(true, "Multipart SMS sent (${parts.size} parts)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            sendSmsViaIntent(phoneNumber, message)
        }
    }

    private fun sendSmsViaIntent(phoneNumber: String, message: String): SmsResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SmsResult(true, "Opened SMS app for sending")
        } catch (e: Exception) {
            SmsResult(false, "Failed to open SMS app: ${e.message}")
        }
    }
}
