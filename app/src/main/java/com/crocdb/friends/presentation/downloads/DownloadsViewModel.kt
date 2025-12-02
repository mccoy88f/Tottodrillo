package com.crocdb.friends.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crocdb.friends.data.repository.DownloadConfigRepository
import com.crocdb.friends.domain.manager.DownloadManager
import com.crocdb.friends.domain.model.DownloadConfig
import com.crocdb.friends.domain.model.DownloadLink
import com.crocdb.friends.domain.model.DownloadTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Stato UI per downloads
 */
data class DownloadsUiState(
    val activeDownloads: List<DownloadTask> = emptyList(),
    val config: DownloadConfig? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel per gestire download
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val configRepository: DownloadConfigRepository
) : ViewModel() {

    // Configurazione download
    val downloadConfig: StateFlow<DownloadConfig> = configRepository.downloadConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadConfig(
                downloadPath = configRepository.getDefaultDownloadPath()
            )
        )

    // Download attivi
    val activeDownloads: StateFlow<List<DownloadTask>> = downloadManager.observeActiveDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    /**
     * Avvia download
     */
    fun startDownload(
        romSlug: String,
        romTitle: String,
        downloadLink: DownloadLink,
        customPath: String? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                downloadManager.startDownload(
                    romSlug = romSlug,
                    romTitle = romTitle,
                    downloadLink = downloadLink,
                    customPath = customPath
                )
                
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "Errore durante avvio download"
                    )
                }
            }
        }
    }

    /**
     * Cancella download
     */
    fun cancelDownload(workId: UUID) {
        downloadManager.cancelDownload(workId)
    }

    /**
     * Cancella tutti i download
     */
    fun cancelAllDownloads() {
        downloadManager.cancelAllDownloads()
    }

    /**
     * Aggiorna path download
     */
    fun updateDownloadPath(path: String) {
        viewModelScope.launch {
            if (configRepository.isPathValid(path)) {
                configRepository.setDownloadPath(path)
            } else {
                _uiState.update { 
                    it.copy(error = "Path non valido o non scrivibile")
                }
            }
        }
    }

    /**
     * Aggiorna estrazione automatica
     */
    fun updateAutoExtract(enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setAutoExtract(enabled)
        }
    }

    /**
     * Aggiorna eliminazione archivi
     */
    fun updateDeleteAfterExtract(enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setDeleteAfterExtract(enabled)
        }
    }

    /**
     * Aggiorna WiFi only
     */
    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setWifiOnly(enabled)
        }
    }

    /**
     * Aggiorna notifiche
     */
    fun updateNotifications(enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setNotificationsEnabled(enabled)
        }
    }

    /**
     * Ottiene spazio disponibile
     */
    fun getAvailableSpace(path: String): Long {
        return configRepository.getAvailableSpace(path)
    }

    /**
     * Resetta al path predefinito
     */
    fun resetToDefaultPath() {
        viewModelScope.launch {
            val defaultPath = configRepository.getDefaultDownloadPath()
            configRepository.setDownloadPath(defaultPath)
        }
    }

    /**
     * Pulisce errore
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
