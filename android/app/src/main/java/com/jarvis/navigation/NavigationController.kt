package com.jarvis.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri

class NavigationController(private val context: Context) {

    fun navigateTo(destination: String): Boolean {
        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            true
        } else false
    }

    fun navigateWalkTo(destination: String): Boolean {
        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            true
        } else false
    }

    fun navigateWithOrigin(origin: String, destination: String): Boolean {
        val url = "https://www.google.com/maps/dir/?api=1" +
                "&origin=${Uri.encode(origin)}" +
                "&destination=${Uri.encode(destination)}" +
                "&travelmode=driving"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }

    fun showOnMap(query: String): Boolean {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }

    fun isGoogleMapsInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.apps.maps", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
