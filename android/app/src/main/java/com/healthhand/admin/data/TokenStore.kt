package com.healthhand.admin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "health_hand_admin",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    val secureStorageAvailable: Boolean
        get() = prefs != null

    private fun requireSecurePrefs(): SharedPreferences = prefs
        ?: throw IllegalStateException("Захищене сховище Android недоступне. Перезапустіть пристрій або перевстановіть застосунок.")

    fun saveToken(token: String) {
        requireSecurePrefs().edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs?.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
    }

    fun saveBaseUrl(url: String) {
        requireSecurePrefs().edit().putString(KEY_BASE_URL, url).apply()
    }

    fun getBaseUrl(): String? = prefs?.getString(KEY_BASE_URL, null)

    companion object {
        private const val KEY_TOKEN = "admin_token"
        private const val KEY_BASE_URL = "base_url"
    }
}
