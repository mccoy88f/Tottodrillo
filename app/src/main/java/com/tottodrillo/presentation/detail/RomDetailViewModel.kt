package com.tottodrillo.presentation.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tottodrillo.data.remote.NetworkResult
import com.tottodrillo.data.remote.getUserMessage
import com.tottodrillo.domain.manager.DownloadManager
import com.tottodrillo.domain.model.DownloadLink
import com.tottodrillo.domain.model.DownloadStatus
import com.tottodrillo.domain.model.ExtractionStatus
import com.tottodrillo.domain.repository.RomRepository
import com.tottodrillo.presentation.common.RomDetailUiState
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
        // Traccia che questa ROM è stata aperta
        viewModelScope.launch {
            if (romSlug.isNotEmpty()) {
                repository.trackRomOpened(romSlug)
            }
        }
    }

    /**
     * Ricarica solo lo stato di download ed estrazione (senza ricaricare la ROM)
     * E riavvia l'osservazione per i download/estrazioni in corso
     */
    fun refreshRomStatus() {
        val currentRom = _uiState.value.rom ?: return
        
        viewModelScope.launch {
            // Calcola lo stato per ogni link separatamente
            val linkStatuses = currentRom.downloadLinks.associate { link ->
                val status = downloadManager.checkLinkStatus(link)
                link.url to status
            }
            
            // Tiene lo stato generale per retrocompatibilità (primo link con stato non Idle)
            val (downloadStatus, extractionStatus) = downloadManager.checkRomStatus(romSlug, currentRom.downloadLinks)
            
            _uiState.update {
                it.copy(
                    downloadStatus = downloadStatus,
                    extractionStatus = extractionStatus,
                    linkStatuses = linkStatuses
                )
            }
            
            // Riavvia l'osservazione per i download/estrazioni in corso
            startObservingActiveTasks(currentRom.downloadLinks)
        }
    }
    
    /**
     * Avvia l'osservazione per i download e estrazioni attivi
     */
    private fun startObservingActiveTasks(downloadLinks: List<DownloadLink>) {
        viewModelScope.launch {
            // Per ogni link, verifica se c'è un download in corso
            downloadLinks.forEach { link ->
                val activeDownloadWorkId = downloadManager.getActiveDownloadWorkId(link.url)
                if (activeDownloadWorkId != null) {
                    // Se non stiamo già osservando questo work, avvia l'osservazione
                    if (currentWorkId != activeDownloadWorkId) {
                        currentWorkId = activeDownloadWorkId
                        observeDownloadForLink(link, activeDownloadWorkId)
                    }
                }
                
                // Verifica se c'è un'estrazione in corso per questo file (se scaricato)
                val linkStatus = _uiState.value.linkStatuses[link.url]
                if (linkStatus?.first is DownloadStatus.Completed) {
                    val completed = linkStatus.first as DownloadStatus.Completed
                    val archivePath = completed.romTitle // Questo è il percorso completo del file
                    
                    val activeExtractionWorkId = downloadManager.getActiveExtractionWorkId(archivePath)
                    if (activeExtractionWorkId != null) {
                        // Se non stiamo già osservando questo work, avvia l'osservazione
                        if (currentExtractionWorkId != activeExtractionWorkId) {
                            currentExtractionWorkId = activeExtractionWorkId
                            observeExtractionForLink(link, activeExtractionWorkId)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Osserva un download per un link specifico
     */
    private fun observeDownloadForLink(link: DownloadLink, workId: UUID) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            downloadManager.observeDownload(workId).collect { task ->
                val status = task?.status ?: DownloadStatus.Idle
                // Aggiorna sia downloadStatus generale che linkStatuses per il link specifico
                val currentRom = _uiState.value.rom
                if (currentRom != null) {
                    val currentLinkStatuses = _uiState.value.linkStatuses.toMutableMap()
                    
                    // Aggiorna lo stato per il link specifico
                    val currentLinkStatus = currentLinkStatuses[link.url]
                    val newLinkStatus = Pair(
                        status, // Nuovo stato download
                        currentLinkStatus?.second ?: ExtractionStatus.Idle // Mantieni stato estrazione
                    )
                    currentLinkStatuses[link.url] = newLinkStatus
                    
                    _uiState.update { 
                        it.copy(
                            downloadStatus = status,
                            linkStatuses = currentLinkStatuses
                        ) 
                    }
                } else {
                    _uiState.update { it.copy(downloadStatus = status) }
                }
                
                // Quando il download termina, ricarica lo stato per verificare se c'è un'estrazione
                if (status is DownloadStatus.Completed) {
                    kotlinx.coroutines.delay(1000) // Aspetta che il file .status sia scritto
                    refreshRomStatus()
                }
            }
        }
    }
    
    /**
     * Avvia l'osservazione di un'estrazione per un link specifico (pubblico per essere chiamato dalla UI)
     */
    fun startObservingExtractionForLink(link: DownloadLink, workId: UUID) {
        if (currentExtractionWorkId != workId) {
            currentExtractionWorkId = workId
            observeExtractionForLink(link, workId)
        }
    }
    
    /**
     * Osserva un'estrazione per un link specifico
     */
    private fun observeExtractionForLink(link: DownloadLink, workId: UUID) {
        // Se stiamo già osservando questo workId, non cancellare e riavviare
        if (currentExtractionWorkId == workId && currentExtractionJob?.isActive == true) {
            android.util.Log.d("RomDetailViewModel", "ℹ️ Già osservando estrazione con workId: $workId, salto riavvio")
            return
        }
        
        currentExtractionJob?.cancel()
        currentExtractionWorkId = workId
        currentExtractionJob = viewModelScope.launch {
            try {
                downloadManager.observeExtraction(workId).collect { status ->
                    // Aggiorna sempre lo stato per il link specifico
                    val currentRom = _uiState.value.rom
                    if (currentRom != null) {
                        val currentLinkStatuses = _uiState.value.linkStatuses.toMutableMap()
                        val currentLinkStatus = currentLinkStatuses[link.url]
                        
                        // Aggiorna lo stato per questo link specifico
                        currentLinkStatuses[link.url] = Pair(
                            currentLinkStatus?.first ?: DownloadStatus.Idle,
                            status
                        )
                        
                        _uiState.update { 
                            it.copy(
                                extractionStatus = status,
                                linkStatuses = currentLinkStatuses
                            ) 
                        }
                    } else {
                        _uiState.update { 
                            it.copy(extractionStatus = status) 
                        }
                    }
                    
                    // Quando l'estrazione termina, aggiorna solo lo stato UI
                    // Non chiamare refreshRomStatus() per evitare di riavviare l'osservazione
                    // Lo stato sarà ricaricato automaticamente quando l'utente rientra nella schermata
                    if (status is ExtractionStatus.Failed) {
                        android.util.Log.e("RomDetailViewModel", "Errore estrazione per link ${link.url}: ${status.error}")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // La cancellazione del job è normale (es. quando il ViewModel viene ricreato o quando si riavvia l'osservazione)
            } catch (e: Exception) {
                android.util.Log.e("RomDetailViewModel", "Errore durante osservazione estrazione per link ${link.url}", e)
            }
        }
    }
    
    /**
     * Osserva un'estrazione (versione generica senza link specifico)
     */
    private fun observeExtraction(workId: UUID) {
        currentExtractionJob?.cancel()
        currentExtractionJob = viewModelScope.launch {
            downloadManager.observeExtraction(workId).collect { status ->
                when (status) {
                    is ExtractionStatus.Completed -> {
                        // Aggiorna immediatamente extractionStatus
                        _uiState.update { 
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
                        // Aggiorna lo stato per il link che ha l'estrazione in corso
                        val currentRom = _uiState.value.rom
                        if (currentRom != null) {
                            val currentLinkStatuses = _uiState.value.linkStatuses.toMutableMap()
                            
                            // Trova il link che corrisponde all'estrazione in corso e aggiorna lo stato
                            currentRom.downloadLinks.forEach { link ->
                                val linkStatus = currentLinkStatuses[link.url]
                                if (linkStatus?.first is DownloadStatus.Completed) {
                                    // Questo link ha un download completato, potrebbe essere quello in estrazione
                                    currentLinkStatuses[link.url] = Pair(linkStatus.first, status)
                                }
                            }
                            
                            _uiState.update { 
                                it.copy(
                                    extractionStatus = status,
                                    linkStatuses = currentLinkStatuses
                                ) 
                            }
                        } else {
                            _uiState.update { 
                                it.copy(extractionStatus = status) 
                            }
                        }
                    }
                    is ExtractionStatus.Failed -> {
                        android.util.Log.e("RomDetailViewModel", "Errore estrazione: ${status.error}")
                        
                        // Aggiorna immediatamente extractionStatus e linkStatuses per mostrare l'icona rossa
                        val currentRom = _uiState.value.rom
                        if (currentRom != null) {
                            // Aggiorna lo stato del link che ha fallito
                            val updatedLinkStatuses = _uiState.value.linkStatuses.toMutableMap()
                            // Trova il link che corrisponde al file estratto (se possibile)
                            currentRom.downloadLinks.forEach { link ->
                                val currentStatus = updatedLinkStatuses[link.url]
                                // Se questo link aveva un'estrazione in corso, segnala il fallimento
                                if (currentStatus?.second is ExtractionStatus.InProgress) {
                                    updatedLinkStatuses[link.url] = Pair(
                                        currentStatus.first,
                                        status
                                    )
                                }
                            }
                            
                            _uiState.update { 
                                it.copy(
                                    extractionStatus = status,
                                    linkStatuses = updatedLinkStatuses
                                ) 
                            }
                        } else {
                            _uiState.update { 
                                it.copy(extractionStatus = status) 
                            }
                        }
                    }
                    else -> {
                        _uiState.update { 
                            it.copy(extractionStatus = status) 
                        }
                    }
                }
            }
        }
    }

    /**
     * Ricarica completamente i dettagli della ROM e lo stato
     */
    fun refreshRomDetail() {
        viewModelScope.launch {
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
                    
                    // Riavvia l'osservazione per i download/estrazioni in corso
                    startObservingActiveTasks(rom.downloadLinks)
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
                // Se il link richiede WebView, apri il WebView headless
                if (link.requiresWebView) {
                    _uiState.update {
                        it.copy(
                            showWebView = true,
                            webViewUrl = link.url,
                            webViewLink = link
                        )
                    }
                } else {
                    // Avvia direttamente il download
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

                        // Usa la funzione helper per osservare il download
                        observeDownloadForLink(link, workId)
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
    }
    
    /**
     * Gestisce l'URL finale estratto dal WebView
     */
    fun onWebViewDownloadUrlExtracted(finalUrl: String, link: DownloadLink) {
        val currentRom = _uiState.value.rom ?: return
        
        // Chiudi il WebView
        _uiState.update {
            it.copy(
                showWebView = false,
                webViewUrl = null,
                webViewLink = null
            )
        }
        
        // Avvia il download con l'URL finale
        viewModelScope.launch {
            try {
                // Aggiorna lo stato per il link originale (quello mostrato nella UI)
                val currentLinkStatuses = _uiState.value.linkStatuses.toMutableMap()
                currentLinkStatuses[link.url] = Pair(
                    DownloadStatus.Pending(currentRom.title),
                    currentLinkStatuses[link.url]?.second ?: ExtractionStatus.Idle
                )
                
                _uiState.update {
                    it.copy(
                        downloadStatus = DownloadStatus.Pending(currentRom.title),
                        linkStatuses = currentLinkStatuses
                    )
                }

                // Crea un nuovo link con l'URL finale e il nome del file aggiornato (se modificato dal WebView)
                val finalLink = link.copy(url = finalUrl, requiresWebView = false)
                
                val workId = downloadManager.startDownload(
                    romSlug = currentRom.slug,
                    romTitle = currentRom.title,
                    downloadLink = finalLink
                )
                currentWorkId = workId

                // Osserva il download e aggiorna lo stato sia per l'URL finale che per quello originale
                currentDownloadJob?.cancel()
                currentDownloadJob = viewModelScope.launch {
                    downloadManager.observeDownload(workId).collect { task ->
                        val status = task?.status ?: DownloadStatus.Idle
                        val currentRom = _uiState.value.rom
                        if (currentRom != null) {
                            val currentLinkStatuses = _uiState.value.linkStatuses.toMutableMap()
                            
                            // Aggiorna lo stato per il link originale (quello mostrato nella UI)
                            val originalLinkStatus = currentLinkStatuses[link.url]
                            currentLinkStatuses[link.url] = Pair(
                                status,
                                originalLinkStatus?.second ?: ExtractionStatus.Idle
                            )
                            
                            // Aggiorna anche per l'URL finale (se diverso)
                            if (finalUrl != link.url) {
                                val finalLinkStatus = currentLinkStatuses[finalUrl]
                                currentLinkStatuses[finalUrl] = Pair(
                                    status,
                                    finalLinkStatus?.second ?: ExtractionStatus.Idle
                                )
                            }
                            
                            _uiState.update { 
                                it.copy(
                                    downloadStatus = status,
                                    linkStatuses = currentLinkStatuses
                                ) 
                            }
                        } else {
                            _uiState.update { it.copy(downloadStatus = status) }
                        }
                        
                        // Quando il download termina, ricarica lo stato per verificare se c'è un'estrazione
                        if (status is DownloadStatus.Completed) {
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
    
    /**
     * Chiude il WebView
     */
    fun onCloseWebView() {
        _uiState.update {
            it.copy(
                showWebView = false,
                webViewUrl = null,
                webViewLink = null
            )
        }
    }

    /**
     * Gestisce il click sul pulsante di estrazione
     */
    fun onExtractClick(archivePath: String, romTitle: String, extractionPath: String) {
        viewModelScope.launch {
            try {
                val currentRom = _uiState.value.rom ?: return@launch
                
                // Trova il link che corrisponde a questo file
                val matchingLink = currentRom.downloadLinks.firstOrNull { link ->
                    val linkStatus = _uiState.value.linkStatuses[link.url]
                    linkStatus?.first is DownloadStatus.Completed && 
                    (linkStatus.first as DownloadStatus.Completed).romTitle == archivePath
                }
                
                // Aggiorna immediatamente lo stato per mostrare che l'estrazione è iniziata
                val currentLinkStatuses = _uiState.value.linkStatuses.toMutableMap()
                if (matchingLink != null) {
                    currentLinkStatuses[matchingLink.url] = Pair(
                        currentLinkStatuses[matchingLink.url]?.first ?: DownloadStatus.Idle,
                        ExtractionStatus.InProgress(0, "Avvio estrazione...")
                    )
                }
                
                _uiState.update {
                    it.copy(
                        extractionStatus = ExtractionStatus.InProgress(0, "Avvio estrazione..."),
                        linkStatuses = currentLinkStatuses
                    )
                }

                val workId = downloadManager.startExtraction(
                    archivePath = archivePath,
                    extractionPath = extractionPath,
                    romTitle = romTitle,
                    romSlug = romSlug // Passa lo slug della ROM corrente
                )
                currentExtractionWorkId = workId
                // Usa la funzione helper per osservare l'estrazione, passando il link se trovato
                if (matchingLink != null) {
                    observeExtractionForLink(matchingLink, workId)
                } else {
                    observeExtraction(workId)
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
     * Ottiene il workId di un'estrazione attiva per un percorso archivio (pubblico per essere chiamato dalla UI)
     */
    suspend fun getActiveExtractionWorkId(archivePath: String): UUID? {
        return downloadManager.getActiveExtractionWorkId(archivePath)
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

                // Verifica se il percorso è su SD card esterna
                val isExternalSd = extractionPath.startsWith("/storage/") && 
                                   !extractionPath.startsWith(android.os.Environment.getExternalStorageDirectory().absolutePath)
                
                if (isExternalSd) {
                    // Per SD card esterna, non possiamo usare file:// URI su Android 7+
                    // Mostriamo un Toast con il percorso e proviamo un Intent generico
                    android.util.Log.d("RomDetailViewModel", "Apertura cartella su SD card: $extractionPath")
                    
                    // Mostra il percorso all'utente
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "Percorso: $extractionPath\nApri manualmente con un file manager",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    // Prova comunque con un Intent generico (potrebbe funzionare su alcuni dispositivi)
                    try {
                        // Prova con ACTION_GET_CONTENT o ACTION_OPEN_DOCUMENT_TREE
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            // Usa un Intent generico che chiede al sistema di aprire la cartella
                            // Alcuni file manager potrebbero gestirlo
                            setType("resource/folder")
                            putExtra("path", extractionPath)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        
                        val chooser = Intent.createChooser(intent, "Apri cartella")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        android.util.Log.e("RomDetailViewModel", "Errore nell'apertura cartella SD card", e)
                        // Il Toast è già stato mostrato sopra
                    }
                } else {
                    // Per memoria locale, usa FileProvider
                    android.util.Log.d("RomDetailViewModel", "Apertura cartella su memoria locale: $extractionPath")
                    try {
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
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.d("RomDetailViewModel", "FileProvider fallito, tentativo con Intent generico", e)
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
                }
            } catch (e: Exception) {
                android.util.Log.e("RomDetailViewModel", "Errore nell'apertura della cartella", e)
            }
        }
    }
}


