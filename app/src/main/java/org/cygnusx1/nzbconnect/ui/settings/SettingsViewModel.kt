package org.cygnusx1.nzbconnect.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cygnusx1.nzbconnect.data.BackupRepository
import org.cygnusx1.nzbconnect.data.SettingsRepository
import org.cygnusx1.nzbconnect.data.newznab.NewznabRepository
import org.cygnusx1.nzbconnect.data.nzbget.NzbgetRepository
import org.cygnusx1.nzbconnect.data.sab.SabnzbdRepository
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.DownloadClientType
import org.cygnusx1.nzbconnect.domain.Indexer
import org.cygnusx1.nzbconnect.domain.NzbgetConfig
import org.cygnusx1.nzbconnect.domain.SabConfig
import javax.inject.Inject

data class SettingsUiState(
    val sab: SabConfig = SabConfig(),
    val nzbget: NzbgetConfig = NzbgetConfig(),
    val activeClient: DownloadClientType = DownloadClientType.SABNZBD,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val newznabRepository: NewznabRepository,
    private val sabnzbdRepository: SabnzbdRepository,
    private val nzbgetRepository: NzbgetRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val indexers: StateFlow<List<Indexer>> =
        settingsRepository.indexers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(
        SettingsUiState(
            sab = settingsRepository.getSabConfig(),
            nzbget = settingsRepository.getNzbgetConfig(),
            activeClient = settingsRepository.getActiveClient(),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun saveIndexer(indexer: Indexer) {
        viewModelScope.launch {
            val id = settingsRepository.upsertIndexer(indexer)
            // Pull caps so category browsing works for this indexer.
            settingsRepository.getIndexer(id)?.let { saved ->
                when (val res = newznabRepository.refreshCategories(saved)) {
                    is ApiResult.Success -> showMessage("Saved ${saved.name} (${res.data.size} categories)")
                    is ApiResult.Failure -> showMessage("Saved, but caps failed: ${res.message}")
                }
            }
        }
    }

    fun deleteIndexer(indexer: Indexer) {
        viewModelScope.launch { settingsRepository.deleteIndexer(indexer) }
    }

    /** Tests the entered (not-yet-saved) indexer values; returns a user-facing result line. */
    suspend fun testIndexer(indexer: Indexer): String = when (val res = newznabRepository.test(indexer)) {
        is ApiResult.Success -> "✓ Reachable"
        is ApiResult.Failure -> "✗ ${res.message}"
    }

    fun saveSab(config: SabConfig) {
        settingsRepository.saveSabConfig(config)
        _state.value = _state.value.copy(sab = settingsRepository.getSabConfig())
        showMessage("SABnzbd settings saved")
    }

    /** Tests the entered (not-yet-saved) SAB values; returns a user-facing result line. */
    suspend fun testSab(config: SabConfig): String = when (val res = sabnzbdRepository.testConnection(config.baseUrl.trim(), config.apiKey.trim())) {
        is ApiResult.Success -> "✓ Connected to SABnzbd ${res.data}"
        is ApiResult.Failure -> "✗ ${res.message}"
    }

    fun saveNzbget(config: NzbgetConfig) {
        settingsRepository.saveNzbgetConfig(config)
        _state.value = _state.value.copy(nzbget = settingsRepository.getNzbgetConfig())
        showMessage("NZBGet settings saved")
    }

    /** Tests the entered (not-yet-saved) NZBGet values; returns a user-facing result line. */
    suspend fun testNzbget(config: NzbgetConfig): String = when (val res = nzbgetRepository.testConnection(config.baseUrl.trim(), config.username.trim(), config.password)) {
        is ApiResult.Success -> "✓ Connected to NZBGet ${res.data}"
        is ApiResult.Failure -> "✗ ${res.message}"
    }

    fun setActiveClient(type: DownloadClientType) {
        settingsRepository.setActiveClient(type)
        _state.value = _state.value.copy(activeClient = type)
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            backupRepository.export(uri).fold(
                onSuccess = { showMessage("Backup saved") },
                onFailure = { showMessage("Backup failed: ${it.message}") },
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            backupRepository.import(uri).fold(
                onSuccess = { count ->
                    _state.value = _state.value.copy(
                        sab = settingsRepository.getSabConfig(),
                        nzbget = settingsRepository.getNzbgetConfig(),
                        activeClient = settingsRepository.getActiveClient(),
                    )
                    showMessage("Restored $count indexer(s)")
                },
                onFailure = { showMessage("Restore failed: ${it.message}") },
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }
}
