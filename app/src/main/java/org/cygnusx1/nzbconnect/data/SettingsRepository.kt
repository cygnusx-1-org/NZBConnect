package org.cygnusx1.nzbconnect.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.cygnusx1.nzbconnect.data.local.IndexerDao
import org.cygnusx1.nzbconnect.data.local.IndexerEntity
import org.cygnusx1.nzbconnect.data.prefs.SecurePrefs
import org.cygnusx1.nzbconnect.domain.DownloadClientType
import org.cygnusx1.nzbconnect.domain.Indexer
import org.cygnusx1.nzbconnect.domain.NzbgetConfig
import org.cygnusx1.nzbconnect.domain.SabConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for user configuration. Indexer metadata lives in Room while
 * the matching API keys (and all SAB config) live in [SecurePrefs].
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val indexerDao: IndexerDao,
    private val prefs: SecurePrefs,
) {

    // Bumped whenever an API key is written/removed. API keys live in prefs, which the
    // Room flow can't observe, so we combine this signal in to force a re-read after a
    // key changes (otherwise a freshly inserted indexer is emitted with an empty key).
    private val keyRevision = MutableStateFlow(0)

    val indexers: Flow<List<Indexer>> =
        combine(indexerDao.observeAll(), keyRevision) { list, _ ->
            list.map { it.toDomain(prefs.indexerApiKey(it.id)) }
        }

    /** One-shot snapshot of all indexers (with API keys), used by backup/export. */
    suspend fun getIndexers(): List<Indexer> =
        indexerDao.getAll().map { it.toDomain(prefs.indexerApiKey(it.id)) }

    suspend fun getIndexer(id: Long): Indexer? =
        indexerDao.getById(id)?.let { it.toDomain(prefs.indexerApiKey(it.id)) }

    suspend fun upsertIndexer(indexer: Indexer): Long {
        val id = if (indexer.id == 0L) {
            indexerDao.insert(
                IndexerEntity(name = indexer.name, baseUrl = indexer.baseUrl, enabled = indexer.enabled),
            )
        } else {
            indexerDao.update(
                IndexerEntity(indexer.id, indexer.name, indexer.baseUrl, indexer.enabled),
            )
            indexer.id
        }
        prefs.setIndexerApiKey(id, indexer.apiKey)
        keyRevision.value++
        return id
    }

    suspend fun deleteIndexer(indexer: Indexer) {
        indexerDao.delete(IndexerEntity(indexer.id, indexer.name, indexer.baseUrl, indexer.enabled))
        prefs.removeIndexerApiKey(indexer.id)
        keyRevision.value++
    }

    fun getSabConfig(): SabConfig = SabConfig(
        baseUrl = prefs.sabBaseUrl,
        apiKey = prefs.sabApiKey,
        defaultCategory = prefs.sabDefaultCategory,
    )

    fun saveSabConfig(config: SabConfig) {
        prefs.sabBaseUrl = config.baseUrl.trim()
        prefs.sabApiKey = config.apiKey.trim()
        prefs.sabDefaultCategory = config.defaultCategory.trim()
    }

    fun getNzbgetConfig(): NzbgetConfig = NzbgetConfig(
        baseUrl = prefs.nzbgetBaseUrl,
        username = prefs.nzbgetUsername,
        password = prefs.nzbgetPassword,
        defaultCategory = prefs.nzbgetDefaultCategory,
    )

    fun saveNzbgetConfig(config: NzbgetConfig) {
        prefs.nzbgetBaseUrl = config.baseUrl.trim()
        prefs.nzbgetUsername = config.username.trim()
        prefs.nzbgetPassword = config.password
        prefs.nzbgetDefaultCategory = config.defaultCategory.trim()
    }

    fun getActiveClient(): DownloadClientType =
        runCatching { DownloadClientType.valueOf(prefs.activeClient) }.getOrDefault(DownloadClientType.SABNZBD)

    fun setActiveClient(type: DownloadClientType) {
        prefs.activeClient = type.name
    }

    private fun IndexerEntity.toDomain(apiKey: String) =
        Indexer(id = id, name = name, baseUrl = baseUrl, enabled = enabled, apiKey = apiKey)
}
