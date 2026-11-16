package com.jarvis.assistant

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API key and settings, encrypted at rest with a hardware-backed Android Keystore key. */
class Prefs(ctx: Context) {
    private val sp = EncryptedSharedPreferences.create(
        ctx, "jarvis_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var lang: String
        get() = sp.getString("lang", "en-IN") ?: "en-IN"
        set(v) = sp.edit().putString("lang", v).apply()
}
