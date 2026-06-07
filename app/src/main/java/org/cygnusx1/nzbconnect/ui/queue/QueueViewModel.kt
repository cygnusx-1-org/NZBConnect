package org.cygnusx1.nzbconnect.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.cygnusx1.nzbconnect.data.sab.SabnzbdRepository
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.QueueItem
import org.cygnusx1.nzbconnect.domain.SabInfo
import org.cygnusx1.nzbconnect.domain.SabWarning
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.ui.formatSize
import org.cygnusx1.nzbconnect.ui.parseSizeMb
import javax.inject.Inject

enum class QueueSortOrder(val label: String) {
    DEFAULT("Default"), NAME("Title"), SIZE("Size"),
    PAUSED("Paused"), CATEGORY("Category"), PERCENTAGE("Percentage"),
}

enum class HistorySortOrder(val label: String) {
    DEFAULT("Default"), NAME("Title"), SIZE("Size"),
    DATE("Date"), CATEGORY("Category"), STATUS("Status"),
}

data class QueueUiState(
    val snapshot: QueueSnapshot? = null,
    val history: List<HistoryItem> = emptyList(),
    val error: String? = null,
    val loadingHistory: Boolean = false,
    val refreshingQueue: Boolean = false,
    val message: String? = null,
    val sabInfo: SabInfo? = null,
    val sabInfoLoading: Boolean = false,
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
    private val repository: SabnzbdRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QueueUiState())
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeQueue().collect { result ->
                _state.value = when (result) {
                    is ApiResult.Success -> _state.value.copy(snapshot = result.data, error = null)
                    is ApiResult.Failure -> _state.value.copy(error = result.message)
                }
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingHistory = true)
            _state.value = when (val res = repository.fetchHistory()) {
                is ApiResult.Success -> _state.value.copy(history = res.data, loadingHistory = false, error = null)
                is ApiResult.Failure -> _state.value.copy(loadingHistory = false, error = res.message)
            }
        }
    }

    fun togglePauseAll() {
        viewModelScope.launch {
            val paused = _state.value.snapshot?.paused == true
            val res = if (paused) repository.resumeAll() else repository.pauseAll()
            reportIfError(res)
            refreshQueueNow()
        }
    }

    fun loadSabInfo() {
        val snap = _state.value.snapshot
        viewModelScope.launch {
            _state.value = _state.value.copy(sabInfoLoading = true)
            val statsResult = repository.fetchServerStats()
            val statusResult = repository.fetchStatus()
            val warningsResult = repository.fetchWarnings()
            if (statsResult is ApiResult.Success) {
                val s = statsResult.data
                val rawWarnings = (warningsResult as? ApiResult.Success)?.data?.warnings ?: emptyList()
                val uptime = (statusResult as? ApiResult.Success)?.data?.status?.uptime ?: "—"
                _state.value = _state.value.copy(
                    sabInfo = SabInfo(
                        downloadToday = formatSize(s.day),
                        downloadWeek = formatSize(s.week),
                        downloadMonth = formatSize(s.month),
                        downloadTotal = formatSize(s.total),
                        freeSpace = snap?.diskSpace?.ifBlank { "—" } ?: "—",
                        uptime = uptime.ifBlank { "—" },
                        onFinish = snap?.finishAction?.ifBlank { "None" } ?: "None",
                        warnings = rawWarnings.map { SabWarning(text = it.text, time = it.time) },
                    ),
                    sabInfoLoading = false,
                )
            } else {
                _state.value = _state.value.copy(
                    sabInfoLoading = false,
                    message = (statsResult as? ApiResult.Failure)?.message,
                )
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

    fun pauseItem(id: String) = act { repository.pauseItem(id) }
    fun resumeItem(id: String) = act { repository.resumeItem(id) }
    fun deleteItem(id: String, deleteFiles: Boolean) = act { repository.deleteItem(id, deleteFiles) }

    fun clearHistory() {
        viewModelScope.launch {
            val res = repository.clearHistory()
            reportIfError(res)
            if (res is ApiResult.Success) refreshHistory()
        }
    }

    fun deleteHistoryItem(id: String, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            val res = repository.deleteHistoryItem(id, deleteFiles)
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
            ids.forEach { id -> repository.deleteHistoryItem(id) }
            _state.value = _state.value.copy(
                historyMultiSelect = false,
                selectedHistoryIds = emptySet(),
            )
            refreshHistory()
        }
    }

    fun setSpeedLimit(percentage: Int) = act { repository.setSpeedLimit(percentage) }

    fun moveItem(id: String, newPosition: Int) {
        viewModelScope.launch { reportIfError(repository.moveItem(id, newPosition)) }
    }

    val sabWebUrl: String get() = repository.sabWebUrl()

    fun restartSabnzbd() {
        viewModelScope.launch { reportIfError(repository.restartSabnzbd()) }
    }

    fun readRssNow() {
        viewModelScope.launch { reportIfError(repository.readRssNow()) }
    }

    fun setFinishAction(action: String) {
        viewModelScope.launch {
            val res = repository.setFinishAction(action)
            reportIfError(res)
            refreshQueueNow()
        }
    }

    private fun act(block: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            reportIfError(block())
            refreshQueueNow()
        }
    }

    private suspend fun refreshQueueNow() {
        when (val res = repository.fetchQueue()) {
            is ApiResult.Success -> _state.value = _state.value.copy(snapshot = res.data)
            is ApiResult.Failure -> Unit
        }
    }

    private fun reportIfError(res: ApiResult<Unit>) {
        if (res is ApiResult.Failure) _state.value = _state.value.copy(message = res.message)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
