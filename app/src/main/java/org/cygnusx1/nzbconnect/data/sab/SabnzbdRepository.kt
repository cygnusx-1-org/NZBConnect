package org.cygnusx1.nzbconnect.data.sab

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.cygnusx1.nzbconnect.data.prefs.SecurePrefs
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.QueueItem
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.domain.SabConfig
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SabnzbdRepository @Inject constructor(
    private val api: SabnzbdApi,
    private val prefs: SecurePrefs,
) {

    private fun config(): SabConfig = SabConfig(
        baseUrl = prefs.sabBaseUrl,
        apiKey = prefs.sabApiKey,
        defaultCategory = prefs.sabDefaultCategory,
    )

    private fun apiUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }

    private fun base(extra: Map<String, String>): Pair<String, Map<String, String>> {
        val cfg = config()
        val params = buildMap {
            put("output", "json")
            put("apikey", cfg.apiKey)
            putAll(extra)
        }
        return apiUrl(cfg.baseUrl) to params
    }

    /** Hand an NZB URL to SAB (`mode=addurl`); SAB fetches it itself. */
    suspend fun addUrl(nzbUrl: String, name: String, category: String?): ApiResult<Unit> {
        val cat = category?.takeIf { it.isNotBlank() } ?: config().defaultCategory
        val extra = buildMap {
            put("mode", "addurl")
            put("name", nzbUrl)
            put("nzbname", name)
            if (cat.isNotBlank()) put("cat", cat)
        }
        return call { val (u, p) = base(extra); api.addUrl(u, p) }.let { res ->
            when (res) {
                is ApiResult.Failure -> res
                is ApiResult.Success ->
                    if (res.data.status) ApiResult.Success(Unit)
                    else ApiResult.Failure("SABnzbd rejected the NZB")
            }
        }
    }

    /** Poll the active queue at [intervalMs] while collected. */
    fun observeQueue(intervalMs: Long = 3000): Flow<ApiResult<QueueSnapshot>> = flow {
        while (true) {
            emit(fetchQueue())
            delay(intervalMs)
        }
    }

    suspend fun fetchQueue(): ApiResult<QueueSnapshot> =
        call { val (u, p) = base(mapOf("mode" to "queue")); api.queue(u, p) }.let { res ->
            when (res) {
                is ApiResult.Failure -> res
                is ApiResult.Success -> {
                    val q = res.data.queue
                    val rawLimit = q.speedLimit.trimEnd('%').toIntOrNull() ?: 100
                    ApiResult.Success(
                        QueueSnapshot(
                            paused = q.paused,
                            speed = q.speed,
                            timeLeft = q.timeLeft,
                            sizeLeft = q.sizeLeft,
                            diskSpace = formatDiskGb(q.diskSpace),
                            finishAction = q.finishAction ?: "None",
                            speedLimit = if (rawLimit == 0) 100 else rawLimit,
                            items = q.slots.map {
                                QueueItem(
                                    id = it.nzoId,
                                    name = it.filename,
                                    status = it.status,
                                    percentage = it.percentage.toIntOrNull() ?: 0,
                                    sizeLeft = it.sizeLeft,
                                    timeLeft = it.timeLeft,
                                    category = it.category,
                                )
                            },
                        ),
                    )
                }
            }
        }

    suspend fun fetchHistory(limit: Int = 50): ApiResult<List<HistoryItem>> =
        call {
            val (u, p) = base(mapOf("mode" to "history", "limit" to limit.toString()))
            api.history(u, p)
        }.let { res ->
            when (res) {
                is ApiResult.Failure -> res
                is ApiResult.Success -> ApiResult.Success(
                    res.data.history.slots.map {
                        HistoryItem(
                            id = it.nzoId,
                            name = it.name,
                            status = it.status,
                            size = it.size,
                            category = it.category,
                            failMessage = it.failMessage,
                            completedMillis = it.completed * 1000,
                        )
                    },
                )
            }
        }

    suspend fun getCategories(): ApiResult<List<String>> =
        call { val (u, p) = base(mapOf("mode" to "get_cats")); api.categories(u, p) }
            .let { res ->
                when (res) {
                    is ApiResult.Failure -> res
                    is ApiResult.Success -> ApiResult.Success(res.data.categories)
                }
            }

    suspend fun fetchStatus(): ApiResult<StatusResponse> =
        call { val (u, p) = base(mapOf("mode" to "status")); api.status(u, p) }

    suspend fun fetchServerStats(): ApiResult<ServerStatsResponse> =
        call { val (u, p) = base(mapOf("mode" to "server_stats")); api.serverStats(u, p) }

    suspend fun fetchWarnings(): ApiResult<WarningsResponse> =
        call { val (u, p) = base(mapOf("mode" to "warnings")); api.warnings(u, p) }

    suspend fun pauseAll(): ApiResult<Unit> = simple(mapOf("mode" to "pause"))
    suspend fun resumeAll(): ApiResult<Unit> = simple(mapOf("mode" to "resume"))

    suspend fun pauseItem(id: String): ApiResult<Unit> =
        simple(mapOf("mode" to "queue", "name" to "pause", "value" to id))

    suspend fun resumeItem(id: String): ApiResult<Unit> =
        simple(mapOf("mode" to "queue", "name" to "resume", "value" to id))

    suspend fun deleteItem(id: String, deleteFiles: Boolean): ApiResult<Unit> =
        simple(
            mapOf(
                "mode" to "queue",
                "name" to "delete",
                "value" to id,
                "del_files" to if (deleteFiles) "1" else "0",
            ),
        )

    suspend fun clearHistory(): ApiResult<Unit> =
        simple(mapOf("mode" to "history", "name" to "delete", "value" to "all"))

    suspend fun deleteHistoryItem(id: String, deleteFiles: Boolean = false): ApiResult<Unit> =
        simple(buildMap {
            put("mode", "history")
            put("name", "delete")
            put("value", id)
            if (deleteFiles) put("del_files", "1")
        })

    fun sabWebUrl(): String = config().baseUrl.trim().trimEnd('/')

    suspend fun restartSabnzbd(): ApiResult<Unit> = simple(mapOf("mode" to "restart"))

    suspend fun readRssNow(): ApiResult<Unit> = simple(mapOf("mode" to "rss_now"))

    suspend fun setFinishAction(action: String): ApiResult<Unit> =
        simple(mapOf("mode" to "queue", "name" to "change_complete_action", "value" to action))

    suspend fun setSpeedLimit(percentage: Int): ApiResult<Unit> =
        simple(mapOf("mode" to "queue", "name" to "speedlimit", "value" to percentage.toString()))

    suspend fun moveItem(id: String, newPosition: Int): ApiResult<Unit> =
        call {
            val (u, p) = base(mapOf("mode" to "switch", "value" to id, "value2" to newPosition.toString()))
            api.switch(u, p)
        }.let { res ->
            when (res) {
                is ApiResult.Failure -> res
                is ApiResult.Success ->
                    if (res.data.result.position >= 0) ApiResult.Success(Unit)
                    else ApiResult.Failure("Could not move queue item")
            }
        }

    /** Connection test for the SAB settings screen (uses the entered, not-yet-saved creds). */
    suspend fun testConnection(baseUrl: String, apiKey: String): ApiResult<String> {
        if (baseUrl.isBlank() || apiKey.isBlank()) return ApiResult.Failure("Enter a URL and API key")
        val params = mapOf("output" to "json", "apikey" to apiKey, "mode" to "version")
        return execute { api.version(apiUrl(baseUrl), params) }.let { res ->
            when (res) {
                is ApiResult.Failure -> res
                is ApiResult.Success ->
                    if (res.data.version.isNotBlank()) ApiResult.Success(res.data.version)
                    else ApiResult.Failure("Unexpected response (check API key)")
            }
        }
    }

    private fun formatDiskGb(gb: String): String {
        val v = gb.toDoubleOrNull() ?: return "—"
        return if (v >= 1000) "%.1f TB".format(v / 1024) else "%.1f GB".format(v)
    }

    private suspend fun simple(extra: Map<String, String>): ApiResult<Unit> =
        call { val (u, p) = base(extra); api.simple(u, p) }.let { res ->
            when (res) {
                is ApiResult.Failure -> res
                is ApiResult.Success ->
                    if (res.data.status) ApiResult.Success(Unit)
                    else ApiResult.Failure(res.data.error ?: "SABnzbd request failed")
            }
        }

    /** Runs a configured-server call, failing fast if SAB isn't set up yet. */
    private suspend fun <T> call(block: suspend () -> Response<T>): ApiResult<T> {
        val cfg = config()
        if (cfg.baseUrl.isBlank() || cfg.apiKey.isBlank()) {
            return ApiResult.Failure("SABnzbd is not configured")
        }
        return execute(block)
    }

    private suspend fun <T> execute(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else ApiResult.Failure("HTTP ${response.code()}")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }
}
