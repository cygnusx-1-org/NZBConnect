package org.cygnusx1.nzbconnect.domain

import kotlinx.coroutines.flow.Flow

/**
 * The download-client operations the app's UI depends on, independent of whether the
 * backing server is SABnzbd or NZBGet. Both [org.cygnusx1.nzbconnect.data.sab.SabnzbdRepository]
 * and [org.cygnusx1.nzbconnect.data.nzbget.NzbgetRepository] implement this, and
 * [org.cygnusx1.nzbconnect.data.DownloadClientRouter] delegates to whichever one is active.
 *
 * Operations a given client can't perform are gated by [capabilities]; their methods return a
 * [ApiResult.Failure] rather than throwing so callers can stay uniform.
 */
interface DownloadClient {

    val capabilities: ClientCapabilities

    /** Poll the active queue at [intervalMs] while collected. */
    fun observeQueue(intervalMs: Long = 3000): Flow<ApiResult<QueueSnapshot>>

    suspend fun fetchQueue(): ApiResult<QueueSnapshot>

    suspend fun fetchHistory(limit: Int = 50): ApiResult<List<HistoryItem>>

    /** Aggregated server stats for the info sidebar. [snapshot] supplies free-space/finish-action. */
    suspend fun fetchServerInfo(snapshot: QueueSnapshot?): ApiResult<ServerInfo>

    suspend fun getCategories(): ApiResult<List<String>>

    /** Hand an NZB URL to the server; it fetches the NZB itself. */
    suspend fun addUrl(nzbUrl: String, name: String, category: String?): ApiResult<Unit>

    suspend fun pauseAll(): ApiResult<Unit>
    suspend fun resumeAll(): ApiResult<Unit>

    suspend fun pauseItem(id: String): ApiResult<Unit>
    suspend fun resumeItem(id: String): ApiResult<Unit>
    suspend fun deleteItem(id: String, deleteFiles: Boolean): ApiResult<Unit>

    suspend fun clearHistory(): ApiResult<Unit>
    suspend fun deleteHistoryItem(id: String, deleteFiles: Boolean = false): ApiResult<Unit>

    /**
     * Set the download speed limit. The unit depends on [ClientCapabilities.speedLimitIsPercentage]:
     * a percentage (0–100, 100 = unlimited) for SAB, or an absolute KB/s (0 = unlimited) for NZBGet.
     */
    suspend fun setSpeedLimit(value: Int): ApiResult<Unit>

    /** Move a queue item to absolute [newPosition] (0-based). */
    suspend fun moveItem(id: String, newPosition: Int): ApiResult<Unit>

    /** Change a queue item's download priority. */
    suspend fun setPriority(id: String, priority: DownloadPriority): ApiResult<Unit>

    /** Set the unpack password for a queue item (encrypted archives). [name] is the item's
     * current name, which SABnzbd's rename-based password API requires. */
    suspend fun setPassword(id: String, name: String, password: String): ApiResult<Unit>

    /** Rename a queue item. */
    suspend fun rename(id: String, newName: String): ApiResult<Unit>

    /** The user-facing web URL of the server, for "open in browser". */
    fun webUrl(): String

    // --- Capability-gated actions ---------------------------------------------

    suspend fun setFinishAction(action: String): ApiResult<Unit>
    suspend fun restart(): ApiResult<Unit>
    suspend fun refreshFeeds(): ApiResult<Unit>
}
