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
import com.tottodrillo.data.repository.DownloadConfigRepository
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
import kotlinx.coroutines.flow.first
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
    private val configRepository: DownloadConfigRepository,
    private val igdbManager: com.tottodrillo.domain.manager.IgdbManager,
    private val romCacheManager: com.tottodrillo.data.repository.RomCacheManager,
    private val platformManager: com.tottodrillo.domain.manager.PlatformManager,
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
        // Carica il provider di ricerca info ROMs
        loadRomInfoSearchProvider()
    }
    
    /**
     * Carica il provider di ricerca info ROMs dalla configurazione
     */
    private fun loadRomInfoSearchProvider() {
        viewModelScope.launch {
            // Carica il valore iniziale
            val config = configRepository.downloadConfig.first()
            _uiState.update {
                it.copy(
                    romInfoSearchProvider = config.romInfoSearchProvider,
                    igdbEnabled = config.igdbEnabled && !config.igdbClientId.isNullOrBlank() && !config.igdbClientSecret.isNullOrBlank()
                )
            }
            
            // Osserva i cambiamenti
            configRepository.downloadConfig.collect { config ->
                _uiState.update {
                    it.copy(
                        romInfoSearchProvider = config.romInfoSearchProvider,
                        igdbEnabled = config.igdbEnabled && !config.igdbClientId.isNullOrBlank() && !config.igdbClientSecret.isNullOrBlank()
                    )
                }
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
                // Cerca prima con l'URL originale del link
                var activeDownloadWorkId = downloadManager.getActiveDownloadWorkId(link.url)
                
                // Se non trova e il link richiede WebView, potrebbe essere stato avviato con un URL finale diverso
                // In questo caso, checkLinkStatus dovrebbe trovare il file .status e l'URL finale
                if (activeDownloadWorkId == null && link.requiresWebView) {
                    // Verifica lo stato del link per vedere se c'è un download in corso
                    val linkStatus = downloadManager.checkLinkStatus(link)
                    if (linkStatus.first is DownloadStatus.InProgress) {
                        // C'è un download in corso, cerca di nuovo con l'URL originale
                        // (getActiveDownloadWorkId ora cerca anche tramite file .status)
                        activeDownloadWorkId = downloadManager.getActiveDownloadWorkId(link.url)
                    }
                }
                
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
            // Cancella la cache della ROM per forzare il refresh completo
            if (repository is com.tottodrillo.data.repository.RomRepositoryImpl) {
                repository.clearRomCache(romSlug)
            }
            loadRomDetail()
        }
    }
    
    /**
     * Ricarica solo i link di download (senza ricaricare info e immagini)
     */
    fun refreshDownloadLinks() {
        val currentRom = _uiState.value.rom ?: return
        loadDownloadLinks(currentRom.slug)
    }

    /**
     * Carica i dettagli della ROM dallo slug
     * Prima carica info e immagini (senza link), poi carica i link in modo asincrono
     */
    private fun loadRomDetail() {
        if (romSlug.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = context.getString(com.tottodrillo.R.string.rom_detail_invalid_slug)
                )
            }
            return
        }

        viewModelScope.launch {
            // PRIMA FASE: Carica ROM senza link (info e immagini)
            _uiState.update { it.copy(isLoading = true, isLoadingLinks = false, error = null) }

            when (val result = repository.getRomBySlug(romSlug, includeDownloadLinks = false)) {
                is NetworkResult.Success -> {
                    val rom = result.data
                    
                    // Aggiorna lo stato con ROM senza link
                    _uiState.update {
                        it.copy(
                            rom = rom,
                            isFavorite = rom.isFavorite,
                            isLoading = false,
                            isLoadingLinks = true, // Inizia il caricamento dei link
                            error = null,
                            igdbImportFailed = false // Reset errore quando si carica una nuova ROM
                        )
                    }
                    
                    // SECONDA FASE: Carica i link in modo asincrono
                    loadDownloadLinks(romSlug)
                }
                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingLinks = false,
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
     * Carica i link di download in modo asincrono
     */
    private fun loadDownloadLinks(slug: String) {
        viewModelScope.launch {
            try {
                when (val result = repository.getRomBySlug(slug, includeDownloadLinks = true)) {
                    is NetworkResult.Success -> {
                        val romWithLinks = result.data
                        val currentRom = _uiState.value.rom
                        
                        // Aggiorna la ROM con i link
                        val updatedRom = currentRom?.copy(
                            downloadLinks = romWithLinks.downloadLinks
                        ) ?: romWithLinks
                        
                        // Calcola lo stato per ogni link separatamente
                        val linkStatuses = updatedRom.downloadLinks.associate { link ->
                            link.url to downloadManager.checkLinkStatus(link)
                        }
                        
                        // Verifica lo stato di download ed estrazione (per retrocompatibilità)
                        val (downloadStatus, extractionStatus) = downloadManager.checkRomStatus(slug, updatedRom.downloadLinks)
                        
                        _uiState.update {
                            it.copy(
                                rom = updatedRom,
                                downloadStatus = downloadStatus,
                                extractionStatus = extractionStatus,
                                linkStatuses = linkStatuses,
                                isLoadingLinks = false
                            )
                        }
                        
                        // Riavvia l'osservazione per i download/estrazioni in corso
                        startObservingActiveTasks(updatedRom.downloadLinks)
                    }
                    is NetworkResult.Error -> {
                        // Se fallisce il caricamento dei link, mostra comunque la ROM senza link
                        _uiState.update {
                            it.copy(
                                isLoadingLinks = false,
                                error = "Errore nel caricamento dei link di download: ${result.exception.getUserMessage()}"
                            )
                        }
                    }
                    is NetworkResult.Loading -> {
                        // già gestito dallo stato isLoadingLinks
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RomDetailViewModel", "Errore caricamento link", e)
                _uiState.update {
                    it.copy(
                        isLoadingLinks = false,
                        error = "Errore nel caricamento dei link: ${e.message}"
                    )
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
            is DownloadStatus.Pending,
            is DownloadStatus.Waiting -> {
                // Annulla il download corrente o l'attesa
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
                    // Se il link richiede un delay, mostra countdown prima di avviare il download
                    if (link.delaySeconds != null && link.delaySeconds > 0) {
                        viewModelScope.launch {
                            try {
                                var remainingSeconds = link.delaySeconds
                                
                                // Aggiorna lo stato con countdown
                                _uiState.update {
                                    it.copy(downloadStatus = DownloadStatus.Waiting(currentRom.title, remainingSeconds))
                                }
                                
                                // Countdown visibile
                                while (remainingSeconds > 0) {
                                    kotlinx.coroutines.delay(1000L)
                                    remainingSeconds--
                                    _uiState.update {
                                        it.copy(downloadStatus = DownloadStatus.Waiting(currentRom.title, remainingSeconds))
                                    }
                                }
                                
                                // Dopo il countdown, avvia il download
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
                                        downloadStatus = DownloadStatus.Idle,
                                        error = e.message ?: context.getString(com.tottodrillo.R.string.rom_detail_download_error)
                                    )
                                }
                            }
                        }
                    } else {
                        // Nessun delay, avvia direttamente il download
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
                                        error = e.message ?: context.getString(com.tottodrillo.R.string.rom_detail_download_error)
                                    )
                                }
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
                    downloadLink = finalLink,
                    originalUrl = link.url // Passa l'URL originale per salvare anche quello nel file .status
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
                        error = e.message ?: context.getString(com.tottodrillo.R.string.rom_detail_download_error)
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
                            e.message ?: context.getString(com.tottodrillo.R.string.rom_detail_extraction_error)
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
                            context.getString(com.tottodrillo.R.string.rom_detail_extraction_path, extractionPath),
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
                        
                        val chooser = Intent.createChooser(intent, context.getString(com.tottodrillo.R.string.rom_detail_open_folder_chooser))
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
                            context.getString(com.tottodrillo.R.string.rom_detail_open_folder_chooser)
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
    
    /**
     * Apre il WebView per la ricerca info ROMs (MobyGames o Gamefaqs)
     */
    fun openMobyGamesSearch(query: String) {
        viewModelScope.launch {
            val config = configRepository.downloadConfig.first()
            val (searchUrl, title) = when (config.romInfoSearchProvider) {
                "gamefaqs" -> {
                    // Per Gamefaqs, sostituisci gli spazi con + invece di usare URLEncoder
                    val encodedQuery = query.replace(" ", "+")
                    "https://gamefaqs.gamespot.com/search?game=$encodedQuery" to 
                    context.getString(com.tottodrillo.R.string.rom_info_search_title_gamefaqs)
                }
                "mobygames" -> {
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                    "https://www.mobygames.com/search/?q=$encodedQuery&type=game" to 
                    context.getString(com.tottodrillo.R.string.rom_info_search_title_mobygames)
                }
                else -> {
                    val encodedQuery = query.replace(" ", "+")
                    "https://gamefaqs.gamespot.com/search?game=$encodedQuery" to 
                    context.getString(com.tottodrillo.R.string.rom_info_search_title_gamefaqs)
                }
            }
            _uiState.update {
                it.copy(
                    showMobyGamesWebView = true,
                    mobyGamesSearchUrl = searchUrl,
                    romInfoSearchTitle = title
                )
            }
        }
    }
    
    /**
     * Chiude il WebView MobyGames/Gamefaqs
     */
    fun closeMobyGamesWebView() {
        _uiState.update {
            it.copy(
                showMobyGamesWebView = false,
                mobyGamesSearchUrl = null,
                romInfoSearchTitle = null
            )
        }
    }
    
    /**
     * Cerca giochi su IGDB per la ROM corrente
     */
    fun searchIgdb() {
        val currentRom = _uiState.value.rom ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingIgdb = true, igdbSearchResults = emptyList()) }
            
            try {
                // Normalizza il titolo per la ricerca (rimuovi parentesi, regioni, anno)
                val normalizedTitle = normalizeTitleForSearch(currentRom.title)
                
                // Ottieni termini di ricerca piattaforma IGDB
                val platformSearchTerms = com.tottodrillo.domain.manager.IgdbPlatformMapper
                    .mapTottodrilloToIgdbSearchTerms(currentRom.platform.code)
                
                // Prova prima con filtro piattaforma (lista termini), poi senza
                var results = igdbManager.searchGames(normalizedTitle, platformSearchTerms)
                
                // Se non trova risultati con filtro, prova senza
                if (results.isEmpty()) {
                    results = igdbManager.searchGames(normalizedTitle)
                }

                // Ordina risultati mettendo in cima quelli che matchano la piattaforma della ROM
                val preferredSorted = results.sortedByDescending { result ->
                    matchesPlatform(currentRom.platform.code, result)
                }
                
                if (results.isEmpty()) {
                    android.util.Log.d("RomDetailViewModel", "Nessun risultato IGDB trovato per: $normalizedTitle")
                    // Nessun risultato: mostra errore
                    _uiState.update {
                        it.copy(
                            isSearchingIgdb = false,
                            igdbSearchResults = emptyList(),
                            igdbImportFailed = true
                        )
                    }
                } else {
                    android.util.Log.d("RomDetailViewModel", "Trovati ${results.size} risultati IGDB")
                    
                    // Se c'è un solo risultato, importa direttamente senza mostrare il dialog
                    if (preferredSorted.size == 1) {
                        _uiState.update {
                            it.copy(
                                isSearchingIgdb = false,
                                igdbSearchResults = emptyList(), // Non mostrare dialog
                                showIgdbImportDialog = false,
                                selectedIgdbResult = null,
                                igdbImportFailed = false // Reset errore
                            )
                        }
                        importIgdbResult(preferredSorted.first())
                    } else {
                        // Più risultati: mostra il dialog di selezione
                        _uiState.update {
                            it.copy(
                                isSearchingIgdb = false,
                                igdbSearchResults = preferredSorted,
                                showIgdbImportDialog = false,
                                selectedIgdbResult = null,
                                igdbImportFailed = false // Reset errore
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RomDetailViewModel", "Errore ricerca IGDB", e)
                _uiState.update {
                    it.copy(
                        isSearchingIgdb = false,
                        igdbSearchResults = emptyList(),
                        igdbImportFailed = true
                    )
                }
            } finally {
                // Safety: assicurati che il flag di ricerca si resetti
                _uiState.update { it.copy(isSearchingIgdb = false) }
            }
        }
    }

    private fun matchesPlatform(targetCode: String, result: com.tottodrillo.domain.model.IgdbSearchResult): Boolean {
        return result.platforms.any { platform ->
            com.tottodrillo.domain.manager.IgdbPlatformMapper.mapIgdbToTottodrillo(platform.name) == targetCode
        }
    }
    
    /**
     * Normalizza il titolo per la ricerca IGDB (rimuove parentesi, regioni, anno)
     */
    private fun normalizeTitleForSearch(title: String): String {
        // Rimuovi pattern comuni: (USA), (EUR), (JPN), (2023), etc.
        return title
            .replace(Regex("\\([^)]*\\)"), "") // Rimuovi tutto tra parentesi
            .replace(Regex("\\[[^\\]]*\\]"), "") // Rimuovi tutto tra quadre
            .trim()
    }
    
    /**
     * Seleziona un risultato IGDB per l'importazione
     */
    /**
     * Seleziona un risultato IGDB e importa subito (senza conferma extra)
     */
    fun importIgdbResult(result: com.tottodrillo.domain.model.IgdbSearchResult) {
        val currentRom = _uiState.value.rom ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingIgdb = true, showIgdbImportDialog = false, selectedIgdbResult = null) }
            
            try {
                val updatedRom = com.tottodrillo.data.mapper.IgdbMapper.mapIgdbToRom(currentRom, result)
                
                romCacheManager.saveRomToCache(updatedRom)
                
                _uiState.update {
                    it.copy(
                        rom = updatedRom,
                        isImportingIgdb = false,
                        igdbSearchResults = emptyList(),
                        selectedIgdbResult = null,
                        igdbImportFailed = false // Successo: reset errore
                    )
                }
                
                android.util.Log.d("RomDetailViewModel", "✅ Dati IGDB importati con successo per: ${updatedRom.title}")
            } catch (e: Exception) {
                android.util.Log.e("RomDetailViewModel", "❌ Errore importazione dati IGDB", e)
                _uiState.update {
                    it.copy(
                        isImportingIgdb = false,
                        igdbImportFailed = true // Errore: mostra rosso
                    )
                }
            }
        }
    }

    /**
     * Mostra il WebView con un URL (riusa il dialog già esistente)
     */
    fun openIgdbWebView(url: String) {
        _uiState.update {
            it.copy(
                showMobyGamesWebView = true,
                mobyGamesSearchUrl = url,
                romInfoSearchTitle = "IGDB"
            )
        }
    }

    /**
     * Chiude/azzera i risultati IGDB
     */
    fun dismissIgdbResults() {
        _uiState.update {
            it.copy(
                igdbSearchResults = emptyList(),
                isSearchingIgdb = false,
                showIgdbImportDialog = false,
                selectedIgdbResult = null
            )
        }
    }
}


