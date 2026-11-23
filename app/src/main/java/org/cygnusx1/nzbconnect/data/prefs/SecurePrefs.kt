package org.cygnusx1.nzbconnect.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores secrets (indexer + download-client API keys and server config) in app-private
 * [SharedPreferences] so they never sit in Room.
 *
 * The file is deliberately not wrapped in [EncryptedSharedPreferences]: its Android
 * Keystore master key is hardware-bound and never leaves the device, so a backup or
 * device-to-device transfer restored the encrypted file onto a phone that could not
 * decrypt it, and every launch crashed. A plain file carries over intact. On-device it
 * is protected by the app sandbox and file-based encryption; Google cloud backups are
 * end-to-end encrypted with the lock-screen credential when the phone has a screen lock,
 * and device transfers are encrypted in transit.
 */
@Singleton
class SecurePrefs @Inject constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).also { migrateLegacy(context, it) }

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

    var nzbgetBaseUrl: String
        get() = prefs.getString(KEY_NZBGET_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NZBGET_URL, value).apply()

    var nzbgetUsername: String
        get() = prefs.getString(KEY_NZBGET_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NZBGET_USER, value).apply()

    var nzbgetPassword: String
        get() = prefs.getString(KEY_NZBGET_PASS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NZBGET_PASS, value).apply()

    var nzbgetDefaultCategory: String
        get() = prefs.getString(KEY_NZBGET_CAT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NZBGET_CAT, value).apply()

    /** Persisted name of the active [org.cygnusx1.nzbconnect.domain.DownloadClientType]. */
    var activeClient: String
        get() = prefs.getString(KEY_ACTIVE_CLIENT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ACTIVE_CLIENT, value).apply()

    private fun keyIndexer(id: Long) = "indexer_apikey_$id"

    private companion object {
        const val TAG = "SecurePrefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Plain prefs file; included in cloud backup and device transfer. */
        const val PREFS_FILE = "nzbconnect_settings"

        /**
         * [EncryptedSharedPreferences] file used by 1.0.2 and earlier. Excluded from
         * backup/transfer by res/xml/backup_rules.xml and res/xml/data_extraction_rules.xml.
         */
        const val LEGACY_PREFS_FILE = "nzbconnect_secrets"

        const val KEY_SAB_URL = "sab_base_url"
        const val KEY_SAB_KEY = "sab_api_key"
        const val KEY_SAB_CAT = "sab_default_category"
        const val KEY_NZBGET_URL = "nzbget_base_url"
        const val KEY_NZBGET_USER = "nzbget_username"
        const val KEY_NZBGET_PASS = "nzbget_password"
        const val KEY_NZBGET_CAT = "nzbget_default_category"
        const val KEY_ACTIVE_CLIENT = "active_download_client"

        /**
         * One-time move of values from the legacy encrypted file into [target], then
         * delete the legacy file and its Keystore master key.
         *
         * - Keys [target] already holds are kept; only missing ones are copied over.
         * - Legacy file was encrypted with another device's Keystore key (restored from a
         *   backup made by 1.0.2 or earlier): drop it and start empty instead of crashing.
         * - Any other failure may be transient (Keystore unavailable), so the legacy file is
         *   kept and the migration retried on the next launch rather than losing settings,
         *   until settings are saved again; after that it is dropped.
         */
        fun migrateLegacy(context: Context, target: SharedPreferences) {
            val legacyFile = File(context.dataDir, "shared_prefs/$LEGACY_PREFS_FILE.xml")
            if (!legacyFile.exists()) return

            val values = try {
                readLegacy(context).also {
                    if (it == null) Log.w(TAG, "Discarding $LEGACY_PREFS_FILE: encrypted with another device's Keystore key")
                }
            } catch (e: Exception) {
                if (target.all.isEmpty()) {
                    Log.w(TAG, "Couldn't read $LEGACY_PREFS_FILE; will retry next launch", e)
                    return
                }
                // Settings were saved since the last failed attempt; stop retrying.
                Log.w(TAG, "Discarding unreadable $LEGACY_PREFS_FILE", e)
                null
            }
            if (values != null) {
                // Values already in target win: they were entered after a failed attempt, or
                // written by an earlier run that committed but died before cleanup.
                val editor = target.edit()
                var copied = 0
                values.forEach { (key, value) ->
                    if (value is String && !target.contains(key)) {
                        editor.putString(key, value)
                        copied++
                    }
                }
                // commit() so the values are on disk before the legacy file is deleted.
                if (!editor.commit()) {
                    Log.w(TAG, "Couldn't write $PREFS_FILE; will retry next launch")
                    return
                }
                Log.i(TAG, "Migrated $copied settings out of $LEGACY_PREFS_FILE")
            }

            context.deleteSharedPreferences(LEGACY_PREFS_FILE)
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }

        /**
         * Decrypts the legacy file. Returns null when it can never be decrypted on this
         * device; throws on any other failure.
         */
        private fun readLegacy(context: Context): Map<String, *>? {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            // The master key never leaves the device, so without one the file came from elsewhere.
            if (!keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) return null
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    LEGACY_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ).all
            } catch (e: Exception) {
                // A master key exists (the old app created one on first launch) but it isn't
                // the key this file was encrypted with.
                if (generateSequence<Throwable>(e) { it.cause }.any { it is AEADBadTagException }) null else throw e
            }
        }
    }
}
