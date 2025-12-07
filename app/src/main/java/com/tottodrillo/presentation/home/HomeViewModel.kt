package com.tottodrillo.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tottodrillo.data.remote.NetworkResult
import com.tottodrillo.data.remote.getUserMessage
import com.tottodrillo.domain.model.SearchFilters
import com.tottodrillo.domain.repository.RomRepository
import com.tottodrillo.presentation.common.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

/**
 * ViewModel per la schermata Home
 * Gestisce lo stato e la logica della home page
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: RomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Traccia i job per poterli cancellare quando si naviga via
    private var favoriteRomsJob: Job? = null
    private var recentRomsJob: Job? = null

    init {
        loadHomeData()
    }
    
    /**
     * Cancella tutti i job attivi (chiamato quando si naviga via dalla home)
     */
    fun cancelActiveJobs() {
        favoriteRomsJob?.cancel()
        recentRomsJob?.cancel()
        favoriteRomsJob = null
        recentRomsJob = null
    }

    /**
     * Carica i dati iniziali della home
     */
    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Carica piattaforme disponibili
            when (val platformsResult = repository.getPlatforms()) {
                is NetworkResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            recentPlatforms = platformsResult.data.take(6),
                            isLoading = false
                        )
                    }
                    
                    // Carica alcuni ROM in evidenza
                    // TEMPORANEAMENTE DISATTIVATO
                    // loadFeaturedRoms()
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
     * Carica ROM in evidenza (ad esempio, giochi popolari)
     * TEMPORANEAMENTE DISATTIVATO - Non chiamare questa funzione
     */
    private fun loadFeaturedRoms() {
        // DISATTIVATO - Non fare nulla per evitare chiamate automatiche a searchRoms
        // viewModelScope.launch {
        //     // Cerca ROM senza query specifica per mostrare risultati generali/popolari
        //     val filters = SearchFilters(query = "")
        //     
        //     when (val result = repository.searchRoms(filters, page = 1)) {
        //         is NetworkResult.Success -> {
        //             _uiState.update { state ->
        //                 state.copy(featuredRoms = result.data.take(10))
        //             }
        //         }
        //         is NetworkResult.Error -> {
        //             // Errore silenzioso per ROM in evidenza
        //             // Non blocca l'interfaccia
        //         }
        //         is NetworkResult.Loading -> {}
        //     }
        // }
    }

    /**
     * Carica ROM preferiti
     */
    fun loadFavoriteRoms() {
        // Cancella il job precedente se esiste
        favoriteRomsJob?.cancel()
        favoriteRomsJob = viewModelScope.launch {
            try {
                val favorites = repository.getFavoriteRoms().first()
                _uiState.update { state ->
                    state.copy(favoriteRoms = favorites.take(10))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellazione normale, non loggare
                throw e
            } catch (e: Exception) {
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
            try {
                val recent = repository.getRecentRoms().first()
                _uiState.update { state ->
                    state.copy(recentRoms = recent)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellazione normale, non loggare
                throw e
            } catch (e: Exception) {
                // Errore silenzioso per ROM recenti
                android.util.Log.e("HomeViewModel", "Errore nel caricamento ROM recenti", e)
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
