package com.crocdb.friends.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crocdb.friends.data.remote.NetworkResult
import com.crocdb.friends.data.remote.getUserMessage
import com.crocdb.friends.domain.model.SearchFilters
import com.crocdb.friends.domain.repository.RomRepository
import com.crocdb.friends.presentation.common.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    init {
        loadHomeData()
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
                    loadFeaturedRoms()
                    // Carica preferiti
                    loadFavoriteRoms()
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
     */
    private fun loadFeaturedRoms() {
        viewModelScope.launch {
            // Cerca alcuni giochi popolari come esempio
            val filters = SearchFilters(query = "mario")
            
            when (val result = repository.searchRoms(filters, page = 1)) {
                is NetworkResult.Success -> {
                    _uiState.update { state ->
                        state.copy(featuredRoms = result.data.take(10))
                    }
                }
                is NetworkResult.Error -> {
                    // Errore silenzioso per ROM in evidenza
                    // Non blocca l'interfaccia
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    /**
     * Carica ROM preferiti
     */
    fun loadFavoriteRoms() {
        viewModelScope.launch {
            try {
                val favorites = repository.getFavoriteRoms().first()
                _uiState.update { state ->
                    state.copy(favoriteRoms = favorites.take(10))
                }
            } catch (e: Exception) {
                // Errore silenzioso per preferiti
                android.util.Log.e("HomeViewModel", "Errore nel caricamento preferiti", e)
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
