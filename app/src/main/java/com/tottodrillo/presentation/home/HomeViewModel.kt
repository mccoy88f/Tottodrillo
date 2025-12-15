package com.tottodrillo.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tottodrillo.data.remote.MobyGamesFeedParser
import com.tottodrillo.data.remote.NetworkResult
import com.tottodrillo.data.remote.getUserMessage
import com.tottodrillo.domain.model.SearchFilters
import com.tottodrillo.domain.repository.RomRepository
import com.tottodrillo.presentation.common.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import javax.inject.Inject

/**
 * ViewModel per la schermata Home
 * Gestisce lo stato e la logica della home page
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: RomRepository,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Traccia i job per poterli cancellare quando si naviga via
    private var favoriteRomsJob: Job? = null
    private var recentRomsJob: Job? = null
    private var downloadedRomsJob: Job? = null

    init {
        loadHomeData()
    }
    
    /**
     * Cancella tutti i job attivi (chiamato quando si naviga via dalla home)
     */
    fun cancelActiveJobs() {
        favoriteRomsJob?.cancel()
        recentRomsJob?.cancel()
        downloadedRomsJob?.cancel()
        favoriteRomsJob = null
        recentRomsJob = null
        downloadedRomsJob = null
    }

    /**
     * Carica i dati iniziali della home
     */
    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // NON pulire la cache: usa i dati in cache per migliorare le performance
            // La cache viene aggiornata automaticamente quando si caricano nuove ROM

            // Carica piattaforme disponibili
            when (val platformsResult = repository.getPlatforms()) {
                is NetworkResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            recentPlatforms = platformsResult.data.take(10),
                            isLoading = false
                        )
                    }
                    
                    // Carica alcuni ROM in evidenza (sempre ricaricati ad ogni inizializzazione)
                    loadFeaturedRoms()
                    // Carica ROM scaricate/installate
                    loadDownloadedRoms()
                    // Carica preferiti
                    loadFavoriteRoms()
                    // Carica ROM recenti
                    loadRecentRoms()
                }
                is NetworkResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = platformsResult.exception.getUserMessage()
                        )
                    }
                }
                is NetworkResult.Loading -> {
                    // Stato già impostato
                }
            }
        }
    }

    /**
     * Carica ROM in evidenza dal feed MobyGames
     */
    fun loadFeaturedRoms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFeatured = true) }
            try {
                val featuredGames = withContext(Dispatchers.IO) {
                    loadMobyGamesFeed()
                }
                _uiState.update { state ->
                    state.copy(
                        featuredGames = featuredGames,
                        isLoadingFeatured = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingFeatured = false) }
                android.util.Log.e("HomeViewModel", "Errore nel caricamento feed MobyGames", e)
                // In caso di errore, lascia la lista vuota (non blocca l'interfaccia)
            }
        }
    }
    
    /**
     * Carica il feed Atom di MobyGames
     */
    private suspend fun loadMobyGamesFeed(): List<com.tottodrillo.domain.model.FeaturedGame> {
        return withContext(Dispatchers.IO) {
            try {
                val feedUrl = MobyGamesFeedParser.getFeedUrl()
                val request = Request.Builder()
                    .url(feedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    android.util.Log.e("HomeViewModel", "Errore nel caricamento feed MobyGames: ${response.code}")
                    return@withContext emptyList()
                }
                
                val inputStream: InputStream = response.body?.byteStream() ?: return@withContext emptyList()
                MobyGamesFeedParser.parseFeed(inputStream)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Errore nel parsing feed MobyGames", e)
                emptyList()
            }
        }
    }

    /**
     * Carica ROM preferiti
     */
    fun loadFavoriteRoms() {
        // Cancella il job precedente se esiste
        favoriteRomsJob?.cancel()
        favoriteRomsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFavorites = true) }
            try {
                val favorites = repository.getFavoriteRoms().first()
                _uiState.update { state ->
                    state.copy(
                        favoriteRoms = favorites.take(10),
                        isLoadingFavorites = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellazione normale, non loggare
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingFavorites = false) }
                // Errore silenzioso per preferiti
                android.util.Log.e("HomeViewModel", "Errore nel caricamento preferiti", e)
            }
        }
    }
    
    /**
     * Carica ROM recenti
     */
    fun loadRecentRoms() {
        // Cancella il job precedente se esiste
        recentRomsJob?.cancel()
        recentRomsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecent = true) }
            try {
                val recent = repository.getRecentRoms().first()
                _uiState.update { state ->
                    state.copy(
                        recentRoms = recent.take(10),
                        isLoadingRecent = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellazione normale, non loggare
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingRecent = false) }
                // Errore silenzioso per ROM recenti
                android.util.Log.e("HomeViewModel", "Errore nel caricamento ROM recenti", e)
            }
        }
    }
    
    /**
     * Carica ROM scaricate e/o installate
     */
    fun loadDownloadedRoms() {
        // Cancella il job precedente se esiste
        downloadedRomsJob?.cancel()
        downloadedRomsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDownloaded = true) }
            try {
                val downloaded = repository.getDownloadedRoms().first()
                _uiState.update { state ->
                    state.copy(
                        downloadedRoms = downloaded.take(10), // Prime 10 ROM, già ordinate per data decrescente
                        isLoadingDownloaded = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellazione normale, non loggare
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDownloaded = false) }
                // Errore silenzioso per ROM scaricate
                android.util.Log.e("HomeViewModel", "Errore nel caricamento ROM scaricate", e)
            }
        }
    }

    /**
     * Ricarica i dati (pull to refresh)
     */
    fun refresh() {
        loadHomeData()
    }

    /**
     * Pulisce l'errore
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
