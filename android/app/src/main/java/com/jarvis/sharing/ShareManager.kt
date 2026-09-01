package com.jarvis.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

class ShareManager(private val context: Context) {
    companion object {
        private const val TAG = "ShareManager"
    }

    fun shareText(text: String, title: String = "Share via"): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, title).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share text", e)
            false
        }
    }

    fun shareTextToApp(text: String, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent); true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share to $packageName", e); false
        }
    }

    fun shareImage(imageUri: Uri, text: String? = null): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                if (text != null) putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share image").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share image", e); false
        }
    }

    fun shareFile(file: File, mimeType: String = "*/*"): Boolean {
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share file").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share file", e); false
        }
    }
}
