package org.cygnusx1.nzbconnect.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cygnusx1.nzbconnect.data.SettingsRepository
import org.cygnusx1.nzbconnect.data.local.SearchHistoryDao
import org.cygnusx1.nzbconnect.data.local.SearchHistoryEntity
import org.cygnusx1.nzbconnect.data.newznab.NewznabRepository
import org.cygnusx1.nzbconnect.data.sab.SabnzbdRepository
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.Indexer
import org.cygnusx1.nzbconnect.domain.NewznabCategory
import org.cygnusx1.nzbconnect.domain.SearchPage
import org.cygnusx1.nzbconnect.domain.SearchResult
import javax.inject.Inject

/** The scope a results screen is showing: a category within an indexer, optionally a query. */
data class ResultContext(
    val indexer: Indexer,
    val categoryId: String, // "" = whole indexer
    val breadcrumb: String,
    val query: String, // "" = browse the category
)

/** Where a pending search will run when the user submits a query from the input screen. */
data class SearchScope(
    val categoryId: String,
    val breadcrumb: String,
)

data class SearchUiState(
    val indexers: List<Indexer> = emptyList(),
    val selectedIndexer: Indexer? = null,
    val categories: List<NewznabCategory> = emptyList(),
    val categoryPath: List<NewznabCategory> = emptyList(),
    val categoriesLoading: Boolean = false,
    val categoriesError: String? = null,
    val resultContext: ResultContext? = null,
    val page: SearchPage? = null,
    val resultsLoading: Boolean = false,
    val resultsError: String? = null,
    val sort: SortOption = SortOption.TIME,
    val filter: ResultFilter = ResultFilter(),
    val pendingScope: SearchScope? = null,
    val selectedResult: SearchResult? = null,
    val sabCategories: List<String> = emptyList(),
    val message: String? = null,
) {
    /** Categories shown at the current drill level. */
    val currentChildren: List<NewznabCategory>
        get() {
            val parentId = categoryPath.lastOrNull()?.id ?: ""
            return categories.filter { it.parentId == parentId }
        }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val newznabRepository: NewznabRepository,
    private val sabnzbdRepository: SabnzbdRepository,
    private val searchHistoryDao: SearchHistoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val recentSearches: StateFlow<List<String>> =
        searchHistoryDao.observeRecent().map { list -> list.map { it.query }.distinct() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsRepository.indexers.collect { indexers ->
                _state.update { it.copy(indexers = indexers.filter { idx -> idx.enabled }) }
            }
        }
    }

    // --- Indexer & category browsing -------------------------------------------

    fun selectIndexer(indexer: Indexer) {
        _state.update {
            it.copy(
                selectedIndexer = indexer,
                categories = emptyList(),
                categoryPath = emptyList(),
                categoriesLoading = true,
                categoriesError = null,
            )
        }
        viewModelScope.launch {
            _state.update {
                when (val res = newznabRepository.getCategories(indexer)) {
                    is ApiResult.Success -> it.copy(categories = res.data, categoriesLoading = false)
                    is ApiResult.Failure -> it.copy(categoriesLoading = false, categoriesError = res.message)
                }
            }
        }
    }

    fun refreshCategories() {
        val indexer = _state.value.selectedIndexer ?: return
        _state.update { it.copy(categoriesLoading = true, categoriesError = null) }
        viewModelScope.launch {
            _state.update {
                when (val res = newznabRepository.refreshCategories(indexer)) {
                    is ApiResult.Success -> it.copy(categories = res.data, categoriesLoading = false)
                    is ApiResult.Failure -> it.copy(categoriesLoading = false, categoriesError = res.message)
                }
            }
        }
    }

    fun hasChildren(category: NewznabCategory): Boolean =
        _state.value.categories.any { it.parentId == category.id }

    fun drillInto(category: NewznabCategory) {
        _state.update { it.copy(categoryPath = it.categoryPath + category) }
    }

    /** Pops one category level; returns false when already at the top level. */
    fun popCategory(): Boolean {
        val path = _state.value.categoryPath
        if (path.isEmpty()) return false
        _state.update { it.copy(categoryPath = path.dropLast(1)) }
        return true
    }

    /** Browse a leaf category (no query) and prepare the results screen. */
    fun browseCategory(category: NewznabCategory) {
        val indexer = _state.value.selectedIndexer ?: return
        val path = _state.value.categoryPath + category
        runQuery(indexer, category.id, breadcrumbOf(path), query = "")
    }

    // --- Searching -------------------------------------------------------------

    /** Remember the scope a subsequent [submitSearch] should run against. */
    fun openSearch(categoryId: String, breadcrumb: String) {
        _state.update { it.copy(pendingScope = SearchScope(categoryId, breadcrumb)) }
    }

    /** Open a text search scoped to the current category level (from the browse FAB). */
    fun openSearchForCurrentLevel() {
        val indexer = _state.value.selectedIndexer ?: return
        val path = _state.value.categoryPath
        val scopeId = path.lastOrNull()?.id ?: ""
        val crumb = breadcrumbOf(path).ifBlank { indexer.name }
        openSearch(scopeId, crumb)
    }

    fun submitSearch(query: String) {
        val indexer = _state.value.selectedIndexer ?: return
        val scope = _state.value.pendingScope ?: SearchScope("", indexer.name)
        if (query.isBlank()) return
        runQuery(indexer, scope.categoryId, scope.breadcrumb, query.trim())
    }

    private fun runQuery(indexer: Indexer, categoryId: String, breadcrumb: String, query: String) {
        _state.update {
            it.copy(
                resultContext = ResultContext(indexer, categoryId, breadcrumb, query),
                resultsLoading = true,
                resultsError = null,
                page = null,
            )
        }
        if (query.isNotBlank()) recordHistory(query, indexer.id)
        viewModelScope.launch {
            _state.update {
                when (val res = newznabRepository.search(indexer, query, categoryId)) {
                    is ApiResult.Success -> it.copy(page = res.data, resultsLoading = false)
                    is ApiResult.Failure -> it.copy(resultsLoading = false, resultsError = res.message)
                }
            }
        }
    }

    fun reloadResults() {
        val ctx = _state.value.resultContext ?: return
        runQuery(ctx.indexer, ctx.categoryId, ctx.breadcrumb, ctx.query)
    }

    // --- Sort / filter / detail ------------------------------------------------

    fun setSort(sort: SortOption) = _state.update { it.copy(sort = sort) }
    fun setFilter(filter: ResultFilter) = _state.update { it.copy(filter = filter) }
    fun clearFilter() = _state.update { it.copy(filter = ResultFilter()) }

    fun selectResult(result: SearchResult) = _state.update { it.copy(selectedResult = result) }
    fun clearSelectedResult() = _state.update { it.copy(selectedResult = null) }

    fun sendSelectedToSab(category: String?) {
        val result = _state.value.selectedResult ?: return
        viewModelScope.launch {
            when (val res = sabnzbdRepository.addUrl(result.nzbUrl, result.title, category)) {
                is ApiResult.Success -> showMessage("Sent “${result.title.take(40)}” to SABnzbd")
                is ApiResult.Failure -> showMessage("Failed: ${res.message}")
            }
        }
    }

    fun loadSabCategories() {
        if (_state.value.sabCategories.isNotEmpty()) return
        viewModelScope.launch {
            when (val res = sabnzbdRepository.getCategories()) {
                is ApiResult.Success -> _state.update { it.copy(sabCategories = res.data) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    // --- History ---------------------------------------------------------------

    fun deleteHistory(query: String) {
        viewModelScope.launch { searchHistoryDao.deleteByQuery(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryDao.clear() }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun showMessage(text: String) = _state.update { it.copy(message = text) }

    private fun recordHistory(query: String, indexerId: Long) {
        viewModelScope.launch {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(
                SearchHistoryEntity(
                    query = query,
                    indexerId = indexerId,
                    searchType = "search",
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun breadcrumbOf(path: List<NewznabCategory>): String =
        path.joinToString(" > ") { it.name }

    private inline fun MutableStateFlow<SearchUiState>.update(block: (SearchUiState) -> SearchUiState) {
        value = block(value)
    }
}
