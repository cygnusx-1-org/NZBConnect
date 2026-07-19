package org.cygnusx1.nzbconnect.data.sab

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.cygnusx1.nzbconnect.data.prefs.SecurePrefs
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.ClientCapabilities
import org.cygnusx1.nzbconnect.domain.DownloadClient
import org.cygnusx1.nzbconnect.domain.DownloadPriority
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.QueueItem
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.domain.SabConfig
import org.cygnusx1.nzbconnect.domain.ServerInfo
import org.cygnusx1.nzbconnect.domain.ServerWarning
import org.cygnusx1.nzbconnect.ui.formatSize
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SabnzbdRepository @Inject constructor(
    private val api: SabnzbdApi,
    private val prefs: SecurePrefs,
) : DownloadClient {

    override val capabilities = ClientCapabilities(
        finishAction = true,
        refreshFeeds = true,
        restart = true,
        speedLimitIsPercentage = true,
    )

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
    override suspend fun addUrl(nzbUrl: String, name: String, category: String?): ApiResult<Unit> {
        val cat = category?.takeIf { it.isNotBlank() } ?: config().defaultCategory
        val extra = buildMap {
            put("mode", "addurl")
            put("name", nzbUrl)
            put("nzbname", name)
            if (cat.isNotBlank()) put("cat", cat)
        }
        return call {
            val (u, p) = base(extra)
            api.addUrl(u, p)
        }.let { res ->
            when (res) {
                is ApiResult.Failure -> res

                is ApiResult.Success ->
                    if (res.data.status) {
                        ApiResult.Success(Unit)
                    } else {
                        ApiResult.Failure("SABnzbd rejected the NZB")
                    }
            }
        }
    }

    /** Poll the active queue at [intervalMs] while collected. */
    override fun observeQueue(intervalMs: Long): Flow<ApiResult<QueueSnapshot>> = flow {
        while (true) {
            emit(fetchQueue())
            delay(intervalMs)
        }
    }

    override suspend fun fetchQueue(): ApiResult<QueueSnapshot> = call {
        val (u, p) = base(mapOf("mode" to "queue"))
        api.queue(u, p)
    }.let { res ->
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

    override suspend fun fetchHistory(limit: Int): ApiResult<List<HistoryItem>> = call {
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

    override suspend fun getCategories(): ApiResult<List<String>> = call {
        val (u, p) = base(mapOf("mode" to "get_cats"))
        api.categories(u, p)
    }
        .let { res ->
            when (res) {
                is ApiResult.Failure -> res
                is ApiResult.Success -> ApiResult.Success(res.data.categories)
            }
        }

    private suspend fun fetchStatus(): ApiResult<StatusResponse> = call {
        val (u, p) = base(mapOf("mode" to "status"))
        api.status(u, p)
    }

    private suspend fun fetchServerStats(): ApiResult<ServerStatsResponse> = call {
        val (u, p) = base(mapOf("mode" to "server_stats"))
        api.serverStats(u, p)
    }

    private suspend fun fetchWarnings(): ApiResult<WarningsResponse> = call {
        val (u, p) = base(mapOf("mode" to "warnings"))
        api.warnings(u, p)
    }

    override suspend fun fetchServerInfo(snapshot: QueueSnapshot?): ApiResult<ServerInfo> {
        val stats = fetchServerStats()
        if (stats is ApiResult.Failure) return stats
        val s = (stats as ApiResult.Success).data
        val status = fetchStatus()
        val warnings = fetchWarnings()
        val uptime = (status as? ApiResult.Success)?.data?.status?.uptime ?: "—"
        val rawWarnings = (warnings as? ApiResult.Success)?.data?.warnings ?: emptyList()
        return ApiResult.Success(
            ServerInfo(
                downloadToday = formatSize(s.day),
                downloadWeek = formatSize(s.week),
                downloadMonth = formatSize(s.month),
                downloadTotal = formatSize(s.total),
                freeSpace = snapshot?.diskSpace?.ifBlank { "—" } ?: "—",
                uptime = uptime.ifBlank { "—" },
                onFinish = snapshot?.finishAction?.ifBlank { "None" } ?: "None",
                warnings = rawWarnings.map { ServerWarning(text = it.text, time = it.time) },
            ),
        )
    }

    override suspend fun pauseAll(): ApiResult<Unit> = simple(mapOf("mode" to "pause"))
    override suspend fun resumeAll(): ApiResult<Unit> = simple(mapOf("mode" to "resume"))

    override suspend fun pauseItem(id: String): ApiResult<Unit> = simple(mapOf("mode" to "queue", "name" to "pause", "value" to id))

    override suspend fun resumeItem(id: String): ApiResult<Unit> = simple(mapOf("mode" to "queue", "name" to "resume", "value" to id))

    override suspend fun deleteItem(id: String, deleteFiles: Boolean): ApiResult<Unit> = simple(
        mapOf(
            "mode" to "queue",
            "name" to "delete",
            "value" to id,
            "del_files" to if (deleteFiles) "1" else "0",
        ),
    )

    override suspend fun clearHistory(): ApiResult<Unit> = simple(mapOf("mode" to "history", "name" to "delete", "value" to "all"))

    override suspend fun deleteHistoryItem(id: String, deleteFiles: Boolean): ApiResult<Unit> = simple(
        buildMap {
            put("mode", "history")
            put("name", "delete")
            put("value", id)
            if (deleteFiles) put("del_files", "1")
        },
    )

    override fun webUrl(): String = config().baseUrl.trim().trimEnd('/')

    override suspend fun restart(): ApiResult<Unit> = simple(mapOf("mode" to "restart"))

    override suspend fun refreshFeeds(): ApiResult<Unit> = simple(mapOf("mode" to "rss_now"))

    override suspend fun setFinishAction(action: String): ApiResult<Unit> = simple(mapOf("mode" to "queue", "name" to "change_complete_action", "value" to action))

    override suspend fun setSpeedLimit(value: Int): ApiResult<Unit> = simple(mapOf("mode" to "queue", "name" to "speedlimit", "value" to value.toString()))

    override suspend fun setPriority(id: String, priority: DownloadPriority): ApiResult<Unit> {
        val value = when (priority) {
            DownloadPriority.FORCE -> 2
            DownloadPriority.HIGH -> 1
            DownloadPriority.NORMAL -> 0
            DownloadPriority.LOW -> -1
            DownloadPriority.STOP -> -4
        }
        return simple(mapOf("mode" to "queue", "name" to "priority", "value" to id, "value2" to value.toString()))
    }

    // SAB sets a job's password through the rename action's third value, keeping the name as-is.
    override suspend fun setPassword(id: String, name: String, password: String): ApiResult<Unit> = simple(
        mapOf(
            "mode" to "queue",
            "name" to "rename",
            "value" to id,
            "value2" to name,
            "value3" to password,
        ),
    )

    override suspend fun rename(id: String, newName: String): ApiResult<Unit> = simple(mapOf("mode" to "queue", "name" to "rename", "value" to id, "value2" to newName))

    override suspend fun moveItem(id: String, newPosition: Int): ApiResult<Unit> = call {
        val (u, p) = base(mapOf("mode" to "switch", "value" to id, "value2" to newPosition.toString()))
        api.switch(u, p)
    }.let { res ->
        when (res) {
            is ApiResult.Failure -> res

            is ApiResult.Success ->
                if (res.data.result.position >= 0) {
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Failure("Could not move queue item")
                }
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
                    if (res.data.version.isNotBlank()) {
                        ApiResult.Success(res.data.version)
                    } else {
                        ApiResult.Failure("Unexpected response (check API key)")
                    }
            }
        }
    }

    private fun formatDiskGb(gb: String): String {
        val v = gb.toDoubleOrNull() ?: return "—"
        return if (v >= 1000) "%.1f TB".format(v / 1024) else "%.1f GB".format(v)
    }

    private suspend fun simple(extra: Map<String, String>): ApiResult<Unit> = call {
        val (u, p) = base(extra)
        api.simple(u, p)
    }.let { res ->
        when (res) {
            is ApiResult.Failure -> res

            is ApiResult.Success ->
                if (res.data.status) {
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Failure(res.data.error ?: "SABnzbd request failed")
                }
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
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Failure("HTTP ${response.code()}")
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }
}
