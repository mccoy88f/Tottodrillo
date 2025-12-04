package com.tottodrillo.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tottodrillo.data.repository.DownloadConfigRepository
import com.tottodrillo.domain.manager.DownloadManager
import com.tottodrillo.domain.model.DownloadConfig
import com.tottodrillo.domain.model.DownloadLink
import com.tottodrillo.domain.model.DownloadTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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

    private val _showClearHistoryDialog = MutableStateFlow(false)
    val showClearHistoryDialog: StateFlow<Boolean> = _showClearHistoryDialog.asStateFlow()

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
     * Avvia estrazione manuale
     */
    fun startExtraction(
        archivePath: String,
        extractionPath: String,
        romTitle: String,
        romSlug: String? = null
    ) {
        viewModelScope.launch {
            try {
                downloadManager.startExtraction(
                    archivePath = archivePath,
                    extractionPath = extractionPath,
                    romTitle = romTitle,
                    romSlug = romSlug
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Errore durante l'estrazione")
                }
            }
        }
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
        // Opzione disabilitata: l'estrazione è sempre manuale
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
     * Aggiorna compatibilità ES-DE
     */
    fun updateEsDeCompatibility(enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setEsDeCompatibility(enabled)
        }
    }

    /**
     * Aggiorna path cartella ROMs ES-DE
     */
    fun updateEsDeRomsPath(path: String) {
        viewModelScope.launch {
            if (configRepository.isPathValid(path)) {
                configRepository.setEsDeRomsPath(path)
            } else {
                _uiState.update { 
                    it.copy(error = "Path non valido o non scrivibile")
                }
            }
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

    /**
     * Mostra dialog di conferma per cancellare storico
     */
    fun showClearHistoryConfirmation() {
        _showClearHistoryDialog.update { true }
    }

    /**
     * Nasconde dialog di conferma
     */
    fun hideClearHistoryDialog() {
        _showClearHistoryDialog.update { false }
    }

    /**
     * Cancella tutti i file .status nella cartella di download predefinita
     */
    fun clearDownloadHistory() {
        viewModelScope.launch {
            try {
                val downloadPath = configRepository.downloadConfig.first().downloadPath
                val downloadDir = File(downloadPath)
                
                if (!downloadDir.exists() || !downloadDir.isDirectory) {
                    _uiState.update { 
                        it.copy(error = "Cartella di download non trovata")
                    }
                    return@launch
                }

                val statusFiles: Array<File>? = downloadDir.listFiles { _: File, name: String ->
                    name.endsWith(".status", ignoreCase = true)
                }

                var deletedCount = 0
                statusFiles?.forEach { file: File ->
                    try {
                        if (file.delete()) {
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadsViewModel", "Errore nel cancellare ${file.name}", e)
                    }
                }

                _uiState.update { 
                    it.copy(error = if (deletedCount > 0) null else "Nessun file .status trovato")
                }
                _showClearHistoryDialog.update { false }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "Errore durante la cancellazione: ${e.message}")
                }
            }
        }
    }
}
