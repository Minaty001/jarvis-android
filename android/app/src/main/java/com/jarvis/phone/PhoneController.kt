package com.jarvis.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

class PhoneController(private val context: Context) {

    fun dial(phoneNumber: String): Boolean {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${phoneNumber}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }

    fun call(phoneNumber: String): Boolean {
        if (!hasCallPermission()) return false
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${phoneNumber}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }

    fun callContact(contactName: String): Boolean {
        val phoneNumber = getPhoneNumber(contactName) ?: return false
        return call(phoneNumber)
    }

    fun dialContact(contactName: String): Boolean {
        val phoneNumber = getPhoneNumber(contactName) ?: return false
        return dial(phoneNumber)
    }

    private fun getPhoneNumber(contactName: String): String? {
        val contentUri = android.net.Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_FILTER_URI,
            Uri.encode(contactName)
        )

        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)

        context.contentResolver.query(
            contentUri, projection, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return cursor.getString(numberIdx)
            }
        }
        return null
    }

    fun hasCallPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
    }
}
