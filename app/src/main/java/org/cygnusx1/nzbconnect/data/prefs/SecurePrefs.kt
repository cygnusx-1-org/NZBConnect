package org.cygnusx1.nzbconnect.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores secrets (indexer + SABnzbd API keys, SAB server config) in an
 * [EncryptedSharedPreferences] file so they never sit in plaintext in Room.
 */
@Singleton
class SecurePrefs @Inject constructor(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nzbconnect_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun indexerApiKey(indexerId: Long): String = prefs.getString(keyIndexer(indexerId), "").orEmpty()

    fun setIndexerApiKey(indexerId: Long, apiKey: String) {
        prefs.edit().putString(keyIndexer(indexerId), apiKey).apply()
    }

    fun removeIndexerApiKey(indexerId: Long) {
        prefs.edit().remove(keyIndexer(indexerId)).apply()
    }

    var sabBaseUrl: String
        get() = prefs.getString(KEY_SAB_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SAB_URL, value).apply()

    var sabApiKey: String
        get() = prefs.getString(KEY_SAB_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SAB_KEY, value).apply()

    var sabDefaultCategory: String
        get() = prefs.getString(KEY_SAB_CAT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SAB_CAT, value).apply()

    private fun keyIndexer(id: Long) = "indexer_apikey_$id"

    private companion object {
        const val KEY_SAB_URL = "sab_base_url"
        const val KEY_SAB_KEY = "sab_api_key"
        const val KEY_SAB_CAT = "sab_default_category"
    }
}
