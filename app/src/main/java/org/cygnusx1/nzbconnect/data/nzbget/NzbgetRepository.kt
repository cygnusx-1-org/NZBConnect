package org.cygnusx1.nzbconnect.data.nzbget

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cygnusx1.nzbconnect.data.prefs.SecurePrefs
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.ClientCapabilities
import org.cygnusx1.nzbconnect.domain.DownloadClient
import org.cygnusx1.nzbconnect.domain.DownloadPriority
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.NzbgetConfig
import org.cygnusx1.nzbconnect.domain.QueueItem
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.domain.ServerInfo
import org.cygnusx1.nzbconnect.domain.ServerWarning
import org.cygnusx1.nzbconnect.ui.formatSize
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Maps NZBGet's JSON-RPC API onto the shared [DownloadClient] contract. */
@Singleton
class NzbgetRepository @Inject constructor(
    private val api: NzbgetApi,
    private val prefs: SecurePrefs,
    private val json: Json,
) : DownloadClient {

    override val capabilities = ClientCapabilities(
        finishAction = false,
        refreshFeeds = false,
        restart = true,
        speedLimitIsPercentage = false,
    )

    private fun config(): NzbgetConfig = NzbgetConfig(
        baseUrl = prefs.nzbgetBaseUrl,
        username = prefs.nzbgetUsername,
        password = prefs.nzbgetPassword,
        defaultCategory = prefs.nzbgetDefaultCategory,
    )

    // --- Queue -----------------------------------------------------------------

    override fun observeQueue(intervalMs: Long): Flow<ApiResult<QueueSnapshot>> = flow {
        while (true) {
            emit(fetchQueue())
            delay(intervalMs)
        }
    }

    override suspend fun fetchQueue(): ApiResult<QueueSnapshot> {
        val statusRes = rpc("status")
        if (statusRes is ApiResult.Failure) return statusRes
        val status = decode((statusRes as ApiResult.Success).data, NzbgetStatus.serializer())

        val groupsRes = rpc("listgroups", buildJsonArray { add(0) })
        if (groupsRes is ApiResult.Failure) return groupsRes
        val groups = decode(
            (groupsRes as ApiResult.Success).data,
            ListSerializer(NzbgetGroup.serializer()),
        )

        val remainingBytes = mb(status.remainingSizeMB)
        return ApiResult.Success(
            QueueSnapshot(
                paused = status.downloadPaused,
                speed = formatRate(status.downloadRate),
                timeLeft = formatEta(remainingBytes, status.downloadRate),
                sizeLeft = if (status.remainingSizeMB > 0) formatSize(remainingBytes) else "",
                diskSpace = formatSize(mb(status.freeDiskSpaceMB)),
                finishAction = "None",
                speedLimit = (status.downloadLimit / 1024).toInt(), // KB/s, 0 = unlimited
                items = groups.map { g ->
                    val left = combineHiLo(g.remainingSizeHi, g.remainingSizeLo)
                    QueueItem(
                        id = g.nzbId.toString(),
                        name = g.nzbName,
                        status = mapStatus(g.status),
                        percentage = nzbgetPercentage(g),
                        sizeLeft = if (left > 0) formatSize(left) else "",
                        timeLeft = "",
                        category = g.category,
                    )
                },
            ),
        )
    }

    override suspend fun fetchHistory(limit: Int): ApiResult<List<HistoryItem>> {
        val res = rpc("history", buildJsonArray { add(false) })
        if (res is ApiResult.Failure) return res
        val items = decode(
            (res as ApiResult.Success).data,
            ListSerializer(NzbgetHistoryItem.serializer()),
        )
        return ApiResult.Success(
            items.take(limit).map {
                HistoryItem(
                    id = it.nzbId.toString(),
                    name = it.name,
                    status = it.status,
                    size = formatSize(combineHiLo(it.fileSizeHi, it.fileSizeLo)),
                    category = it.category,
                    failMessage = if (it.status.contains("FAILURE", ignoreCase = true)) it.status else "",
                    completedMillis = it.historyTime * 1000,
                )
            },
        )
    }

    override suspend fun fetchServerInfo(snapshot: QueueSnapshot?): ApiResult<ServerInfo> {
        val statusRes = rpc("status")
        if (statusRes is ApiResult.Failure) return statusRes
        val s = decode((statusRes as ApiResult.Success).data, NzbgetStatus.serializer())

        val logRes = rpc("log", buildJsonArray { add(0); add(50) })
        val warnings = if (logRes is ApiResult.Success) {
            decode(logRes.data, ListSerializer(NzbgetLogEntry.serializer()))
                .filter { it.kind.equals("WARNING", true) || it.kind.equals("ERROR", true) }
                .map { ServerWarning(text = it.text, time = it.time.toString()) }
        } else {
            emptyList()
        }

        return ApiResult.Success(
            ServerInfo(
                downloadToday = formatSize(mb(s.daySizeMB)),
                downloadWeek = "—", // NZBGet does not track a weekly total
                downloadMonth = formatSize(mb(s.monthSizeMB)),
                downloadTotal = formatSize(mb(s.downloadedSizeMB)),
                freeSpace = snapshot?.diskSpace?.ifBlank { null } ?: formatSize(mb(s.freeDiskSpaceMB)),
                uptime = formatUptime(s.upTimeSec),
                onFinish = "None",
                warnings = warnings,
            ),
        )
    }

    // --- Actions ---------------------------------------------------------------

    override suspend fun getCategories(): ApiResult<List<String>> {
        val res = rpc("config")
        if (res is ApiResult.Failure) return res
        val items = decode((res as ApiResult.Success).data, ListSerializer(NzbgetConfigItem.serializer()))
        return ApiResult.Success(nzbgetCategories(items))
    }

    override suspend fun addUrl(nzbUrl: String, name: String, category: String?): ApiResult<Unit> {
        val cat = category?.takeIf { it.isNotBlank() } ?: config().defaultCategory
        // NZBGet saves the fetched NZB under this filename; its scanner only picks up *.nzb,
        // so the name MUST end in .nzb or the download is silently skipped (SCAN_SKIPPED).
        val filename = if (name.endsWith(".nzb", ignoreCase = true)) name else "$name.nzb"
        val params = buildJsonArray {
            add(filename)  // NZBFilename — must end in .nzb (NZBGet fetches the URL itself)
            add(nzbUrl)    // NZBContent — a URL, which NZBGet downloads itself
            add(cat)       // Category
            add(0)         // Priority
            add(false)     // AddToTop
            add(false)     // AddPaused
            add("")        // DupeKey
            add(0)         // DupeScore
            add("SCORE")   // DupeMode
            addJsonArray {} // PPParameters
        }
        return when (val res = rpc("append", params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val id = (res.data as? JsonPrimitive)?.intOrNull ?: 0
                if (id > 0) ApiResult.Success(Unit) else ApiResult.Failure("NZBGet rejected the NZB")
            }
        }
    }

    override suspend fun pauseAll(): ApiResult<Unit> = rpcBool("pausedownload")
    override suspend fun resumeAll(): ApiResult<Unit> = rpcBool("resumedownload")

    override suspend fun pauseItem(id: String): ApiResult<Unit> =
        editQueue("GroupPause", 0, listOf(id.toInt()))

    override suspend fun resumeItem(id: String): ApiResult<Unit> =
        editQueue("GroupResume", 0, listOf(id.toInt()))

    override suspend fun deleteItem(id: String, deleteFiles: Boolean): ApiResult<Unit> =
        editQueue(if (deleteFiles) "GroupFinalDelete" else "GroupDelete", 0, listOf(id.toInt()))

    override suspend fun clearHistory(): ApiResult<Unit> {
        val res = rpc("history", buildJsonArray { add(false) })
        if (res is ApiResult.Failure) return res
        val ids = decode((res as ApiResult.Success).data, ListSerializer(NzbgetHistoryItem.serializer()))
            .map { it.nzbId }
        if (ids.isEmpty()) return ApiResult.Success(Unit)
        return editQueue("HistoryFinalDelete", 0, ids)
    }

    override suspend fun deleteHistoryItem(id: String, deleteFiles: Boolean): ApiResult<Unit> =
        editQueue(if (deleteFiles) "HistoryFinalDelete" else "HistoryDelete", 0, listOf(id.toInt()))

    /** [value] is an absolute KB/s limit (0 = unlimited) for NZBGet. */
    override suspend fun setSpeedLimit(value: Int): ApiResult<Unit> =
        rpcBool("rate", buildJsonArray { add(value) })

    override suspend fun moveItem(id: String, newPosition: Int): ApiResult<Unit> {
        // NZBGet moves are relative, so translate the absolute target into an offset.
        val groupsRes = rpc("listgroups", buildJsonArray { add(0) })
        if (groupsRes is ApiResult.Failure) return groupsRes
        val groups = decode((groupsRes as ApiResult.Success).data, ListSerializer(NzbgetGroup.serializer()))
        val current = groups.indexOfFirst { it.nzbId.toString() == id }
        if (current < 0) return ApiResult.Failure("Item no longer in queue")
        val offset = newPosition - current
        if (offset == 0) return ApiResult.Success(Unit)
        return editQueue("GroupMoveOffset", offset, listOf(id.toInt()))
    }

    override suspend fun setPriority(id: String, priority: DownloadPriority): ApiResult<Unit> = when (priority) {
        DownloadPriority.FORCE -> editQueueText("GroupSetPriority", "900", listOf(id.toInt()))
        DownloadPriority.HIGH -> editQueueText("GroupSetPriority", "100", listOf(id.toInt()))
        DownloadPriority.NORMAL -> editQueueText("GroupSetPriority", "0", listOf(id.toInt()))
        DownloadPriority.LOW -> editQueueText("GroupSetPriority", "-100", listOf(id.toInt()))
        // NZBGet has no "stop" priority; pausing the item is the closest equivalent.
        DownloadPriority.STOP -> editQueue("GroupPause", 0, listOf(id.toInt()))
    }

    override suspend fun setPassword(id: String, name: String, password: String): ApiResult<Unit> =
        editQueueText("GroupSetParameter", "*Unpack:Password=$password", listOf(id.toInt()))

    override suspend fun rename(id: String, newName: String): ApiResult<Unit> =
        editQueueText("GroupSetName", newName, listOf(id.toInt()))

    override fun webUrl(): String = config().baseUrl.trim().trimEnd('/')

    override suspend fun restart(): ApiResult<Unit> = rpcBool("reload")

    override suspend fun setFinishAction(action: String): ApiResult<Unit> =
        ApiResult.Failure("Not supported by NZBGet")

    override suspend fun refreshFeeds(): ApiResult<Unit> =
        ApiResult.Failure("Not supported by NZBGet")

    /** Connection test for the settings screen (uses entered, not-yet-saved creds). */
    suspend fun testConnection(baseUrl: String, username: String, password: String): ApiResult<String> {
        if (baseUrl.isBlank() || username.isBlank()) return ApiResult.Failure("Enter a URL and username")
        return try {
            val resp = api.rpc(rpcUrl(baseUrl), authHeader(username, password), request("version"))
            val body = resp.body()
            when {
                resp.code() == 401 -> ApiResult.Failure("Authentication failed (check username/password)")
                !resp.isSuccessful -> ApiResult.Failure("HTTP ${resp.code()}")
                body == null -> ApiResult.Failure("Empty response")
                hasError(body) -> ApiResult.Failure(rpcError(body))
                else -> {
                    val version = (body["result"] as? JsonPrimitive)?.contentOrNull
                    if (!version.isNullOrBlank()) ApiResult.Success(version)
                    else ApiResult.Failure("Unexpected response")
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    // --- JSON-RPC plumbing -----------------------------------------------------

    /** `editqueue(Command, Offset, EditText, IDs[])` — the verb behind item pause/resume/delete/move. */
    private suspend fun editQueue(command: String, offset: Int, ids: List<Int>): ApiResult<Unit> =
        rpcBool(
            "editqueue",
            buildJsonArray {
                add(command)
                add(offset)
                add("")
                addJsonArray { ids.forEach { add(it) } }
            },
        )

    /** `editqueue` variant carrying a value in the EditText field (priority, password, name). */
    private suspend fun editQueueText(command: String, text: String, ids: List<Int>): ApiResult<Unit> =
        rpcBool(
            "editqueue",
            buildJsonArray {
                add(command)
                add(0)
                add(text)
                addJsonArray { ids.forEach { add(it) } }
            },
        )

    private suspend fun rpc(method: String, params: JsonArray = EMPTY_PARAMS): ApiResult<JsonElement> {
        val cfg = config()
        if (!cfg.isConfigured) return ApiResult.Failure("NZBGet is not configured")
        return try {
            val resp = api.rpc(rpcUrl(cfg.baseUrl), authHeader(cfg.username, cfg.password), request(method, params))
            val body = resp.body()
            when {
                resp.code() == 401 -> ApiResult.Failure("Authentication failed (check username/password)")
                !resp.isSuccessful -> ApiResult.Failure("HTTP ${resp.code()}")
                body == null -> ApiResult.Failure("Empty response")
                hasError(body) -> ApiResult.Failure(rpcError(body))
                else -> ApiResult.Success(body["result"] ?: JsonNull)
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    private suspend fun rpcBool(method: String, params: JsonArray = EMPTY_PARAMS): ApiResult<Unit> =
        when (val res = rpc(method, params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success ->
                if ((res.data as? JsonPrimitive)?.booleanOrNull == true) ApiResult.Success(Unit)
                else ApiResult.Failure("NZBGet rejected the request")
        }

    private fun <T> decode(element: JsonElement, deserializer: kotlinx.serialization.DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(deserializer, element)

    private fun request(method: String, params: JsonArray = EMPTY_PARAMS): JsonObject = buildJsonObject {
        put("method", method)
        put("params", params)
    }

    private fun hasError(body: JsonObject): Boolean = body["error"].let { it != null && it !is JsonNull }

    private fun rpcError(body: JsonObject): String =
        (body["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            ?: "NZBGet request failed"

    private fun rpcUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/jsonrpc")) trimmed else "$trimmed/jsonrpc"
    }

    private fun authHeader(username: String, password: String): String {
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }

    private fun mb(valueMb: Long): Long = valueMb * 1024L * 1024L

    private fun formatRate(bytesPerSec: Long): String =
        if (bytesPerSec <= 0) "" else "${formatSize(bytesPerSec)}/s"

    private fun formatEta(remainingBytes: Long, rate: Long): String {
        if (rate <= 0 || remainingBytes <= 0) return ""
        val secs = remainingBytes / rate
        return "%d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
    }

    private fun formatUptime(sec: Long): String {
        if (sec <= 0) return "—"
        val d = sec / 86400
        val h = (sec % 86400) / 3600
        val m = (sec % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    private fun mapStatus(status: String): String = when (status.uppercase()) {
        "DOWNLOADING" -> "Downloading"
        "PAUSED" -> "Paused"
        "QUEUED" -> "Queued"
        "FETCHING" -> "Fetching"
        else -> status.lowercase().replaceFirstChar { it.uppercase() }
    }

    private companion object {
        val EMPTY_PARAMS = JsonArray(emptyList())
    }
}

// --- Pure mapping helpers (unit-tested) ----------------------------------------

/** Download progress for a queue group, 0–100, derived from total vs remaining bytes. */
internal fun nzbgetPercentage(group: NzbgetGroup): Int {
    val total = combineHiLo(group.fileSizeHi, group.fileSizeLo)
    if (total <= 0) return 0
    val remaining = combineHiLo(group.remainingSizeHi, group.remainingSizeLo)
    val done = (total - remaining).coerceIn(0, total)
    return ((done * 100) / total).toInt()
}

/** Extract the configured category names (`CategoryN.Name`) from a `config` response. */
internal fun nzbgetCategories(items: List<NzbgetConfigItem>): List<String> {
    val categoryName = Regex("""Category\d+\.Name""")
    return items.filter { categoryName.matches(it.name) }.map { it.value }.filter { it.isNotBlank() }
}
