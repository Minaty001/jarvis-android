package com.jarvis.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PairingStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "jarvis_pairing_store",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_PAIRING_SECRET = "pairing_secret"
        private const val KEY_IS_ENROLLED = "is_enrolled"
    }

    val installationId: String
        get() = synchronized(this) {
            prefs.getString(KEY_INSTALLATION_ID, null)
                ?: java.util.UUID.randomUUID().toString()
                    .also { prefs.edit().putString(KEY_INSTALLATION_ID, it).apply() }
        }

    fun savePairingSecret(secret: String) {
        prefs.edit()
            .putString(KEY_PAIRING_SECRET, secret)
            .putBoolean(KEY_IS_ENROLLED, true)
            .apply()
    }

    fun getPairingSecret(): String? = prefs.getString(KEY_PAIRING_SECRET, null)

    fun isEnrolled(): Boolean = prefs.getBoolean(KEY_IS_ENROLLED, false)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
