package com.crocdb.friends.presentation.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crocdb.friends.data.remote.NetworkResult
import com.crocdb.friends.data.remote.getUserMessage
import com.crocdb.friends.domain.manager.DownloadManager
import com.crocdb.friends.domain.model.DownloadLink
import com.crocdb.friends.domain.model.DownloadStatus
import com.crocdb.friends.domain.model.ExtractionStatus
import com.crocdb.friends.domain.repository.RomRepository
import com.crocdb.friends.presentation.common.RomDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel per la schermata di dettaglio ROM
 */
@HiltViewModel
class RomDetailViewModel @Inject constructor(
    private val repository: RomRepository,
    private val downloadManager: DownloadManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val romSlug: String = savedStateHandle["romSlug"] ?: ""

    private val _uiState = MutableStateFlow(RomDetailUiState(isLoading = true))
    val uiState: StateFlow<RomDetailUiState> = _uiState.asStateFlow()

    private var currentDownloadJob: Job? = null
    private var currentWorkId: UUID? = null
    
    private var currentExtractionJob: Job? = null
    private var currentExtractionWorkId: UUID? = null

    init {
        loadRomDetail()
    }

    /**
     * Ricarica solo lo stato di download ed estrazione (senza ricaricare la ROM)
     */
    fun refreshRomStatus() {
        val currentRom = _uiState.value.rom ?: return
        
        viewModelScope.launch {
            // Calcola lo stato per ogni link separatamente
            val linkStatuses = currentRom.downloadLinks.associate { link ->
                link.url to downloadManager.checkLinkStatus(link)
            }
            
            // Tiene lo stato generale per retrocompatibilità (primo link con stato non Idle)
            val (downloadStatus, extractionStatus) = downloadManager.checkRomStatus(romSlug, currentRom.downloadLinks)
            
            android.util.Log.d("RomDetailViewModel", "🔄 Refresh stato ROM: Download=$downloadStatus, Estrazione=$extractionStatus, LinkStatuses=${linkStatuses.size}")
            _uiState.update {
                it.copy(
                    downloadStatus = downloadStatus,
                    extractionStatus = extractionStatus,
                    linkStatuses = linkStatuses
                )
            }
        }
    }

    /**
     * Ricarica completamente i dettagli della ROM e lo stato
     */
    fun refreshRomDetail() {
        viewModelScope.launch {
            android.util.Log.d("RomDetailViewModel", "🔄 Refresh completo ROM detail")
            loadRomDetail()
        }
    }

    /**
     * Carica i dettagli della ROM dallo slug
     */
    private fun loadRomDetail() {
        if (romSlug.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Slug ROM non valido"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = repository.getRomBySlug(romSlug)) {
                is NetworkResult.Success -> {
                    val rom = result.data
                    
                    // Calcola lo stato per ogni link separatamente
                    val linkStatuses = rom.downloadLinks.associate { link ->
                        link.url to downloadManager.checkLinkStatus(link)
                    }
                    
                    // Verifica lo stato di download ed estrazione (per retrocompatibilità)
                    val (downloadStatus, extractionStatus) = downloadManager.checkRomStatus(romSlug, rom.downloadLinks)
                    
                    _uiState.update {
                        it.copy(
                            rom = rom,
                            isFavorite = rom.isFavorite,
                            downloadStatus = downloadStatus,
                            extractionStatus = extractionStatus,
                            linkStatuses = linkStatuses,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.exception.getUserMessage()
                        )
                    }
                }
                is NetworkResult.Loading -> {
                    // già gestito dallo stato isLoading
                }
            }
        }
    }

    /**
     * Gestisce il toggle dei preferiti
     */
    fun toggleFavorite() {
        val currentRom = _uiState.value.rom ?: return

        viewModelScope.launch {
            val currentlyFavorite = repository.isFavorite(currentRom.slug)
            val result = if (currentlyFavorite) {
                repository.removeFromFavorites(currentRom.slug)
            } else {
                repository.addToFavorites(currentRom)
            }

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        rom = currentRom.copy(isFavorite = !currentlyFavorite),
                        isFavorite = !currentlyFavorite
                    )
                }
            }
        }
    }

    /**
     * Gestisce il click sul pulsante di download:
     * - se un download è in corso, lo annulla
     * - altrimenti avvia (o riavvia) il download
     */
    fun onDownloadButtonClick(link: DownloadLink) {
        val currentRom = _uiState.value.rom ?: return

        when (uiState.value.downloadStatus) {
            is DownloadStatus.InProgress,
            is DownloadStatus.Pending -> {
                // Annulla il download corrente
                currentWorkId?.let { workId ->
                    downloadManager.cancelDownload(workId)
                }
                _uiState.update { it.copy(downloadStatus = DownloadStatus.Idle) }
            }
            else -> {
                // Avvia o riavvia il download
                viewModelScope.launch {
                    try {
                        _uiState.update {
                            it.copy(downloadStatus = DownloadStatus.Pending(currentRom.title))
                        }

                        val workId = downloadManager.startDownload(
                            romSlug = currentRom.slug,
                            romTitle = currentRom.title,
                            downloadLink = link
                        )
                        currentWorkId = workId

                        currentDownloadJob?.cancel()
                        currentDownloadJob = launch {
                            downloadManager.observeDownload(workId).collect { task ->
                                val status = task?.status ?: DownloadStatus.Idle
                                android.util.Log.d("RomDetailViewModel", "📥 Stato download ricevuto: ${status.javaClass.simpleName}")
                                _uiState.update { it.copy(downloadStatus = status) }
                                
                                // Quando il download termina, ricarica lo stato per verificare se c'è un'estrazione
                                if (status is DownloadStatus.Completed) {
                                    android.util.Log.d("RomDetailViewModel", "✅ Download completato, ricarico stato completo")
                                    // Aggiorna immediatamente lo stato UI con il download completato
                                    // Il refresh verrà fatto dopo per verificare l'estrazione
                                    kotlinx.coroutines.delay(1000) // Aspetta che il file .status sia scritto
                                    refreshRomStatus()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                error = e.message ?: "Errore nell'avvio del download"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Gestisce il click sul pulsante di estrazione
     */
    fun onExtractClick(archivePath: String, romTitle: String, extractionPath: String) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(extractionStatus = ExtractionStatus.Idle)
                }

                val workId = downloadManager.startExtraction(
                    archivePath = archivePath,
                    extractionPath = extractionPath,
                    romTitle = romTitle,
                    romSlug = romSlug // Passa lo slug della ROM corrente
                )
                currentExtractionWorkId = workId

                currentExtractionJob?.cancel()
                currentExtractionJob = launch {
                    android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] Inizio osservazione estrazione per workId: $workId")
                    downloadManager.observeExtraction(workId).collect { status ->
                        android.util.Log.i("RomDetailViewModel", "📥 [PASSO 3] Ricevuto nuovo stato estrazione: ${status.javaClass.simpleName}")
                        when (status) {
                            is ExtractionStatus.Completed -> {
                                android.util.Log.i("RomDetailViewModel", "✅ [PASSO 3] ExtractionStatus.Completed ricevuto! Path: ${status.extractedPath}, Files: ${status.filesCount}")
                                
                                // Aggiorna immediatamente extractionStatus
                                _uiState.update { 
                                    android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] Aggiornamento immediato extractionStatus con Completed")
                                    it.copy(extractionStatus = status) 
                                }
                                
                                // Attendi che il file .status sia scritto e poi aggiorna linkStatuses
                                kotlinx.coroutines.delay(1000)
                                
                                val currentRom = _uiState.value.rom
                                if (currentRom != null) {
                                    // Ricalcola lo stato per tutti i link
                                    val updatedLinkStatuses = currentRom.downloadLinks.associate { link ->
                                        link.url to downloadManager.checkLinkStatus(link)
                                    }
                                    
                                    _uiState.update { 
                                        android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] Aggiornamento linkStatuses dopo estrazione completata")
                                        it.copy(
                                            extractionStatus = status,
                                            linkStatuses = updatedLinkStatuses
                                        ) 
                                    }
                                }
                                
                                // Ricarica lo stato completo per assicurarsi che tutto sia sincronizzato
                                refreshRomStatus()
                            }
                            is ExtractionStatus.InProgress -> {
                                android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] ExtractionStatus.InProgress: ${status.progress}% - ${status.currentFile}")
                                _uiState.update { 
                                    android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] Aggiornamento UI state con ExtractionStatus.InProgress")
                                    it.copy(extractionStatus = status) 
                                }
                            }
                            is ExtractionStatus.Failed -> {
                                android.util.Log.e("RomDetailViewModel", "❌ [PASSO 3] ExtractionStatus.Failed: ${status.error}")
                                _uiState.update { 
                                    android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] Aggiornamento UI state con ExtractionStatus.Failed")
                                    it.copy(extractionStatus = status) 
                                }
                            }
                            else -> {
                                android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] ExtractionStatus.Idle")
                                _uiState.update { 
                                    android.util.Log.d("RomDetailViewModel", "🔄 [PASSO 3] Aggiornamento UI state con ExtractionStatus.Idle")
                                    it.copy(extractionStatus = status) 
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        extractionStatus = ExtractionStatus.Failed(
                            e.message ?: "Errore nell'avvio dell'estrazione"
                        )
                    )
                }
            }
        }
    }

    /**
     * Apre la cartella di estrazione usando un Intent
     */
    fun openExtractionFolder(extractionPath: String) {
        viewModelScope.launch {
            try {
                val folder = File(extractionPath)
                if (!folder.exists() || !folder.isDirectory) {
                    android.util.Log.e("RomDetailViewModel", "Cartella non trovata: $extractionPath")
                    return@launch
                }

                // Prova ad aprire con un file manager usando ACTION_VIEW
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        folder
                    )
                    setDataAndType(uri, "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                // Se non riesce, prova con un Intent generico per aprire la cartella
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.d("RomDetailViewModel", "Tentativo con Intent generico")
                    // Fallback: apri con un file manager generico
                    val chooser = Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse("file://$extractionPath"), "resource/folder")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        "Apri cartella"
                    )
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                android.util.Log.e("RomDetailViewModel", "Errore nell'apertura della cartella", e)
            }
        }
    }
}


