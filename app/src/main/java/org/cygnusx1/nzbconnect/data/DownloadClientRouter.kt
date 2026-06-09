package org.cygnusx1.nzbconnect.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.cygnusx1.nzbconnect.data.nzbget.NzbgetRepository
import org.cygnusx1.nzbconnect.data.sab.SabnzbdRepository
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.ClientCapabilities
import org.cygnusx1.nzbconnect.domain.DownloadClient
import org.cygnusx1.nzbconnect.domain.DownloadClientType
import org.cygnusx1.nzbconnect.domain.DownloadPriority
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.domain.ServerInfo
import org.cygnusx1.nzbconnect.domain.displayName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [DownloadClient] that forwards every call to whichever concrete client the user has
 * selected in Settings. The active client is re-read on each call (and on each poll tick of
 * [observeQueue]) so switching clients takes effect immediately, without recreating ViewModels.
 */
@Singleton
class DownloadClientRouter @Inject constructor(
    private val sab: SabnzbdRepository,
    private val nzbget: NzbgetRepository,
    private val settings: SettingsRepository,
) : DownloadClient {

    fun activeType(): DownloadClientType = settings.getActiveClient()

    /** Display name of the active client, for UI labels. */
    fun activeName(): String = activeType().displayName

    /** The concrete client for a given type. */
    fun client(type: DownloadClientType): DownloadClient = when (type) {
        DownloadClientType.SABNZBD -> sab
        DownloadClientType.NZBGET -> nzbget
    }

    fun name(type: DownloadClientType): String = type.displayName

    /** Clients the user has actually configured (URL + creds present). */
    fun configuredClients(): List<DownloadClientType> = buildList {
        if (settings.getSabConfig().isConfigured) add(DownloadClientType.SABNZBD)
        if (settings.getNzbgetConfig().isConfigured) add(DownloadClientType.NZBGET)
    }

    /**
     * The client to preselect: the configured "active" choice when it's actually configured,
     * otherwise the first configured client, falling back to the active setting.
     */
    fun defaultClient(): DownloadClientType {
        val active = activeType()
        val configured = configuredClients()
        return if (active in configured) active else configured.firstOrNull() ?: active
    }

    private fun active(): DownloadClient = client(activeType())

    override val capabilities: ClientCapabilities get() = active().capabilities

    override fun observeQueue(intervalMs: Long): Flow<ApiResult<QueueSnapshot>> = flow {
        while (true) {
            emit(active().fetchQueue())
            delay(intervalMs)
        }
    }

    override suspend fun fetchQueue(): ApiResult<QueueSnapshot> = active().fetchQueue()
    override suspend fun fetchHistory(limit: Int): ApiResult<List<HistoryItem>> = active().fetchHistory(limit)
    override suspend fun fetchServerInfo(snapshot: QueueSnapshot?): ApiResult<ServerInfo> =
        active().fetchServerInfo(snapshot)
    override suspend fun getCategories(): ApiResult<List<String>> = active().getCategories()
    override suspend fun addUrl(nzbUrl: String, name: String, category: String?): ApiResult<Unit> =
        active().addUrl(nzbUrl, name, category)
    override suspend fun pauseAll(): ApiResult<Unit> = active().pauseAll()
    override suspend fun resumeAll(): ApiResult<Unit> = active().resumeAll()
    override suspend fun pauseItem(id: String): ApiResult<Unit> = active().pauseItem(id)
    override suspend fun resumeItem(id: String): ApiResult<Unit> = active().resumeItem(id)
    override suspend fun deleteItem(id: String, deleteFiles: Boolean): ApiResult<Unit> =
        active().deleteItem(id, deleteFiles)
    override suspend fun clearHistory(): ApiResult<Unit> = active().clearHistory()
    override suspend fun deleteHistoryItem(id: String, deleteFiles: Boolean): ApiResult<Unit> =
        active().deleteHistoryItem(id, deleteFiles)
    override suspend fun setSpeedLimit(value: Int): ApiResult<Unit> = active().setSpeedLimit(value)
    override suspend fun moveItem(id: String, newPosition: Int): ApiResult<Unit> =
        active().moveItem(id, newPosition)
    override suspend fun setPriority(id: String, priority: DownloadPriority): ApiResult<Unit> =
        active().setPriority(id, priority)
    override suspend fun setPassword(id: String, name: String, password: String): ApiResult<Unit> =
        active().setPassword(id, name, password)
    override suspend fun rename(id: String, newName: String): ApiResult<Unit> = active().rename(id, newName)
    override fun webUrl(): String = active().webUrl()
    override suspend fun setFinishAction(action: String): ApiResult<Unit> = active().setFinishAction(action)
    override suspend fun restart(): ApiResult<Unit> = active().restart()
    override suspend fun refreshFeeds(): ApiResult<Unit> = active().refreshFeeds()
}
