package org.cygnusx1.nzbconnect.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.cygnusx1.nzbconnect.domain.Indexer
import org.cygnusx1.nzbconnect.domain.SabConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Serializable snapshot of all user configuration. Note: API keys are stored in clear. */
@Serializable
data class BackupData(
    val version: Int = 1,
    val indexers: List<BackupIndexer> = emptyList(),
    val sab: BackupSab = BackupSab(),
)

@Serializable
data class BackupIndexer(
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val apiKey: String = "",
)

@Serializable
data class BackupSab(
    val baseUrl: String = "",
    val apiKey: String = "",
    val defaultCategory: String = "",
)

/**
 * Exports/imports indexer and SABnzbd configuration to a user-chosen JSON file via the
 * Storage Access Framework. Restore replaces the current configuration.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** Write the current configuration to [uri] as JSON. */
    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = BackupData(
                indexers = settingsRepository.getIndexers().map {
                    BackupIndexer(it.name, it.baseUrl, it.enabled, it.apiKey)
                },
                sab = settingsRepository.getSabConfig().let {
                    BackupSab(it.baseUrl, it.apiKey, it.defaultCategory)
                },
            )
            val text = json.encodeToString(BackupData.serializer(), data)
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                ?: error("Could not open file for writing")
        }
    }

    /** Replace the current configuration with the contents of [uri]. Returns indexer count. */
    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read file")
            val data = json.decodeFromString(BackupData.serializer(), text)

            // Replace existing indexers (deleteIndexer also clears the stored API key).
            settingsRepository.getIndexers().forEach { settingsRepository.deleteIndexer(it) }
            data.indexers.forEach {
                settingsRepository.upsertIndexer(
                    Indexer(name = it.name, baseUrl = it.baseUrl, enabled = it.enabled, apiKey = it.apiKey),
                )
            }
            settingsRepository.saveSabConfig(
                SabConfig(data.sab.baseUrl, data.sab.apiKey, data.sab.defaultCategory),
            )
            data.indexers.size
        }
    }
}
