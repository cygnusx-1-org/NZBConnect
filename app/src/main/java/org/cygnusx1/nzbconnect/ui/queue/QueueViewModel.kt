package org.cygnusx1.nzbconnect.ui.queue

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.cygnusx1.nzbconnect.data.DownloadClientRouter
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.ClientCapabilities
import org.cygnusx1.nzbconnect.domain.DownloadClientType
import org.cygnusx1.nzbconnect.domain.DownloadPriority
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.QueueItem
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.domain.ServerInfo
import org.cygnusx1.nzbconnect.ui.parseSizeMb
import javax.inject.Inject

enum class QueueSortOrder(val label: String) {
    DEFAULT("Default"),
    NAME("Title"),
    SIZE("Size"),
    PAUSED("Paused"),
    CATEGORY("Category"),
    PERCENTAGE("Percentage"),
}

enum class HistorySortOrder(val label: String) {
    DEFAULT("Default"),
    NAME("Title"),
    SIZE("Size"),
    DATE("Date"),
    CATEGORY("Category"),
    STATUS("Status"),
}

data class QueueUiState(
    val snapshot: QueueSnapshot? = null,
    val history: List<HistoryItem> = emptyList(),
    val error: String? = null,
    val loadingHistory: Boolean = false,
    val refreshingQueue: Boolean = false,
    val message: String? = null,
    val serverInfo: ServerInfo? = null,
    val serverInfoLoading: Boolean = false,
    val clientName: String = "SABnzbd",
    val selectedClient: DownloadClientType = DownloadClientType.SABNZBD,
    val availableClients: List<DownloadClientType> = emptyList(),
    val capabilities: ClientCapabilities = ClientCapabilities(
        finishAction = true,
        refreshFeeds = true,
        restart = true,
        speedLimitIsPercentage = true,
    ),
    val queueSort: QueueSortOrder = QueueSortOrder.DEFAULT,
    val historySort: HistorySortOrder = HistorySortOrder.DEFAULT,
    val historyMultiSelect: Boolean = false,
    val selectedHistoryIds: Set<String> = emptySet(),
) {
    val sortedQueueItems: List<QueueItem> get() {
        val items = snapshot?.items ?: return emptyList()
        return when (queueSort) {
            QueueSortOrder.DEFAULT -> items
            QueueSortOrder.NAME -> items.sortedBy { it.name.lowercase() }
            QueueSortOrder.SIZE -> items.sortedByDescending { parseSizeMb(it.sizeLeft) }
            QueueSortOrder.PAUSED -> items.sortedBy { if (it.status.equals("paused", true)) 0 else 1 }
            QueueSortOrder.CATEGORY -> items.sortedBy { it.category.lowercase() }
            QueueSortOrder.PERCENTAGE -> items.sortedByDescending { it.percentage }
        }
    }
    val sortedHistoryItems: List<HistoryItem> get() = when (historySort) {
        HistorySortOrder.DEFAULT -> history
        HistorySortOrder.NAME -> history.sortedBy { it.name.lowercase() }
        HistorySortOrder.SIZE -> history.sortedByDescending { parseSizeMb(it.size) }
        HistorySortOrder.DATE -> history.sortedByDescending { it.completedMillis }
        HistorySortOrder.CATEGORY -> history.sortedBy { it.category.lowercase() }
        HistorySortOrder.STATUS -> history.sortedBy { it.status.lowercase() }
    }
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: DownloadClientRouter,
) : ViewModel() {

    private val _state: MutableStateFlow<QueueUiState>
    val state: StateFlow<QueueUiState>

    /** The running queue-poll loop for the selected client; cancelled/restarted on switch. */
    private var pollJob: Job? = null

    /** Bumped on every user action / authoritative refresh. A poll started before a bump is
     * discarded on completion so a stale in-flight fetch can't revert an optimistic change. */
    private var stateVersion = 0

    /** >0 while a mutating action is running; polls don't apply their results meanwhile. */
    private var pendingMutations = 0

    /**
     * Per-item pause intent (id -> intended-paused). NZBGet keeps reporting an actively
     * downloading item as "Downloading" for several seconds after a pause, so we keep showing
     * the user's intent and ignore the contradicting server status until it catches up.
     */
    private val pendingItemPause = mutableMapOf<String, Boolean>()

    init {
        val available = repository.configuredClients()
        val selected = repository.defaultClient()
        _state = MutableStateFlow(
            QueueUiState(
                selectedClient = selected,
                availableClients = available,
                clientName = repository.name(selected),
                capabilities = repository.client(selected).capabilities,
            ),
        )
        state = _state.asStateFlow()
        startPolling()
    }

    /** The client currently shown on the Downloads screen. */
    private fun current() = repository.client(_state.value.selectedClient)

    private fun startPolling() {
        pollJob?.cancel()
        val client = current()
        pollJob = viewModelScope.launch {
            Log.d("QueueVM", "poll loop START client=${_state.value.selectedClient}")
            try {
                while (isActive) {
                    val versionAtStart = stateVersion
                    val result = client.fetchQueue()
                    val paused = (result as? ApiResult.Success)?.data?.paused
                    val items = (result as? ApiResult.Success)?.data?.items
                        ?.joinToString { "${it.id}:${it.status}" }
                    // Drop the result if an action ran while this fetch was in flight (or is still
                    // running), so a stale "downloading" can't overwrite a just-applied pause.
                    if (pendingMutations == 0 && stateVersion == versionAtStart) {
                        Log.d("QueueVM", "poll APPLY paused=$paused items=[$items] (v=$versionAtStart)")
                        _state.value = when (result) {
                            is ApiResult.Success -> _state.value.copy(snapshot = reconcile(result.data), error = null)
                            is ApiResult.Failure -> _state.value.copy(error = result.message)
                        }
                    } else {
                        Log.d("QueueVM", "poll SKIP paused=$paused (vStart=$versionAtStart vNow=$stateVersion pending=$pendingMutations)")
                    }
                    delay(2000)
                }
            } catch (e: Throwable) {
                Log.e("QueueVM", "poll loop CRASHED", e)
                throw e
            } finally {
                Log.d("QueueVM", "poll loop EXIT (isActive=$isActive)")
            }
        }
    }

    /** Switch the Downloads screen to a different configured client. */
    fun selectClient(type: DownloadClientType) {
        if (type == _state.value.selectedClient) return
        stateVersion++
        _state.value = _state.value.copy(
            selectedClient = type,
            clientName = repository.name(type),
            capabilities = repository.client(type).capabilities,
            snapshot = null,
            history = emptyList(),
            serverInfo = null,
            error = null,
        )
        startPolling()
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingHistory = true)
            _state.value = when (val res = current().fetchHistory()) {
                is ApiResult.Success -> _state.value.copy(history = res.data, loadingHistory = false, error = null)
                is ApiResult.Failure -> _state.value.copy(loadingHistory = false, error = res.message)
            }
        }
    }

    fun togglePauseAll() {
        val snap = _state.value.snapshot ?: return
        val wasPaused = snap.paused
        Log.d("QueueVM", "togglePauseAll(GLOBAL) wasPaused=$wasPaused -> optimistic ${!wasPaused}")
        // Optimistic: flip immediately so the button responds without waiting for the round-trip.
        _state.value = _state.value.copy(snapshot = snap.copy(paused = !wasPaused))
        act { if (wasPaused) current().resumeAll() else current().pauseAll() }
    }

    fun loadServerInfo() {
        val snap = _state.value.snapshot
        viewModelScope.launch {
            _state.value = _state.value.copy(serverInfoLoading = true)
            _state.value = when (val res = current().fetchServerInfo(snap)) {
                is ApiResult.Success -> _state.value.copy(serverInfo = res.data, serverInfoLoading = false)
                is ApiResult.Failure -> _state.value.copy(serverInfoLoading = false, message = res.message)
            }
        }
    }

    fun refreshQueue() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshingQueue = true)
            refreshQueueNow()
            _state.value = _state.value.copy(refreshingQueue = false)
        }
    }

    fun pauseItem(id: String) {
        Log.d("QueueVM", "pauseItem(ITEM) id=$id -> optimistic Paused")
        pendingItemPause[id] = true
        updateItem(id) { it.copy(status = "Paused") }
        act { current().pauseItem(id) }
    }

    fun resumeItem(id: String) {
        Log.d("QueueVM", "resumeItem(ITEM) id=$id -> optimistic Queued")
        pendingItemPause[id] = false
        updateItem(id) { it.copy(status = "Queued") }
        act { current().resumeItem(id) }
    }

    /**
     * Overlay the user's per-item pause intent onto a server snapshot: keep showing the intended
     * status until the server agrees, then clear the intent. Intents for items that have left the
     * queue are dropped.
     */
    private fun reconcile(snap: QueueSnapshot): QueueSnapshot {
        if (pendingItemPause.isEmpty()) return snap
        pendingItemPause.keys.retainAll(snap.items.map { it.id }.toSet())
        if (pendingItemPause.isEmpty()) return snap
        val items = snap.items.map { item ->
            val intendPaused = pendingItemPause[item.id] ?: return@map item
            val serverPaused = item.status.equals("paused", ignoreCase = true)
            if (serverPaused == intendPaused) {
                pendingItemPause.remove(item.id) // server caught up
                item
            } else if (intendPaused) {
                item.copy(status = "Paused")
            } else {
                item.copy(status = if (item.status.equals("paused", true)) "Queued" else item.status)
            }
        }
        return snap.copy(items = items)
    }

    fun deleteItem(id: String, deleteFiles: Boolean) {
        removeItem(id)
        act { current().deleteItem(id, deleteFiles) }
    }

    /** Optimistically patch a single queue item in the current snapshot. */
    private fun updateItem(id: String, transform: (QueueItem) -> QueueItem) {
        val snap = _state.value.snapshot ?: return
        _state.value = _state.value.copy(
            snapshot = snap.copy(items = snap.items.map { if (it.id == id) transform(it) else it }),
        )
    }

    private fun removeItem(id: String) {
        val snap = _state.value.snapshot ?: return
        _state.value = _state.value.copy(snapshot = snap.copy(items = snap.items.filterNot { it.id == id }))
    }

    fun clearHistory() {
        viewModelScope.launch {
            val res = current().clearHistory()
            reportIfError(res)
            if (res is ApiResult.Success) refreshHistory()
        }
    }

    fun deleteHistoryItem(id: String, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            val res = current().deleteHistoryItem(id, deleteFiles)
            reportIfError(res)
            if (res is ApiResult.Success) refreshHistory()
        }
    }

    fun setQueueSort(sort: QueueSortOrder) {
        _state.value = _state.value.copy(queueSort = sort)
    }

    fun setHistorySort(sort: HistorySortOrder) {
        _state.value = _state.value.copy(historySort = sort)
    }

    fun toggleHistoryMultiSelect() {
        _state.value = _state.value.copy(
            historyMultiSelect = !_state.value.historyMultiSelect,
            selectedHistoryIds = emptySet(),
        )
    }

    fun toggleHistoryItemSelection(id: String) {
        val current = _state.value.selectedHistoryIds
        _state.value = _state.value.copy(
            selectedHistoryIds = if (id in current) current - id else current + id,
        )
    }

    fun deleteSelectedHistory() {
        val ids = _state.value.selectedHistoryIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> current().deleteHistoryItem(id) }
            _state.value = _state.value.copy(
                historyMultiSelect = false,
                selectedHistoryIds = emptySet(),
            )
            refreshHistory()
        }
    }

    fun setSpeedLimit(value: Int) = act { current().setSpeedLimit(value) }

    fun moveItem(id: String, newPosition: Int) {
        viewModelScope.launch { reportIfError(current().moveItem(id, newPosition)) }
    }

    // --- Per-item overflow actions ---------------------------------------------

    fun setItemPriority(id: String, priority: DownloadPriority) = act { current().setPriority(id, priority) }

    fun setItemPassword(id: String, name: String, password: String) = act { current().setPassword(id, name, password) }

    fun renameItem(id: String, newName: String) = act { current().rename(id, newName) }

    fun moveItemToTop(id: String) = act { current().moveItem(id, 0) }

    fun moveItemToEnd(id: String) {
        val items = _state.value.snapshot?.items ?: return
        if (items.isNotEmpty()) act { current().moveItem(id, items.lastIndex) }
    }

    fun moveItemUp(id: String, by: Int = 10) {
        val items = _state.value.snapshot?.items ?: return
        val cur = items.indexOfFirst { it.id == id }
        if (cur >= 0) act { current().moveItem(id, (cur - by).coerceAtLeast(0)) }
    }

    fun moveItemDown(id: String, by: Int = 10) {
        val items = _state.value.snapshot?.items ?: return
        val cur = items.indexOfFirst { it.id == id }
        if (cur >= 0) act { current().moveItem(id, (cur + by).coerceAtMost(items.lastIndex)) }
    }

    val webUrl: String get() = current().webUrl()

    fun restart() {
        viewModelScope.launch { reportIfError(current().restart()) }
    }

    fun refreshFeeds() {
        viewModelScope.launch { reportIfError(current().refreshFeeds()) }
    }

    fun setFinishAction(action: String) {
        viewModelScope.launch {
            val res = current().setFinishAction(action)
            reportIfError(res)
            refreshQueueNow()
        }
    }

    private fun act(block: suspend () -> ApiResult<Unit>) {
        pendingMutations++
        stateVersion++
        Log.d("QueueVM", "act START pending=$pendingMutations v=$stateVersion")
        viewModelScope.launch {
            try {
                val r = block()
                Log.d("QueueVM", "act block result=$r")
                reportIfError(r)
                refreshQueueNow()
            } finally {
                pendingMutations--
                Log.d("QueueVM", "act END pending=$pendingMutations")
            }
        }
    }

    private suspend fun refreshQueueNow() {
        val res = current().fetchQueue()
        stateVersion++ // invalidate any poll that was in flight during this authoritative fetch
        val paused = (res as? ApiResult.Success)?.data?.paused
        val items = (res as? ApiResult.Success)?.data?.items?.joinToString { "${it.id}:${it.status}" }
        Log.d("QueueVM", "refreshQueueNow APPLY paused=$paused items=[$items] (v=$stateVersion)")
        if (res is ApiResult.Success) _state.value = _state.value.copy(snapshot = reconcile(res.data))
    }

    private fun reportIfError(res: ApiResult<Unit>) {
        if (res is ApiResult.Failure) _state.value = _state.value.copy(message = res.message)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
