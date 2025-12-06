package com.tottodrillo.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tottodrillo.data.remote.NetworkResult
import com.tottodrillo.data.remote.getUserMessage
import com.tottodrillo.domain.manager.SourceManager
import com.tottodrillo.domain.model.SearchFilters
import com.tottodrillo.domain.repository.RomRepository
import com.tottodrillo.presentation.common.SearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel per la schermata Ricerca
 * Gestisce ricerca, filtri e paginazione
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: RomRepository,
    private val sourceManager: SourceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadFiltersData()
        observeSearchQuery()
    }
    
    /**
     * Inizializza i filtri con una piattaforma specifica
     */
    fun initializeWithPlatform(platformCode: String) {
        _uiState.update { state ->
            state.copy(
                filters = state.filters.copy(selectedPlatforms = listOf(platformCode)),
                currentPage = 1
            )
        }
        performSearch()
    }

    /**
     * Osserva le modifiche alla query con debounce
     */
    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(500) // Attende 500ms dopo l'ultimo input
                .distinctUntilChanged()
                .collect { query ->
                    if (query.length >= 2 || query.isEmpty()) {
                        performSearch()
                    }
                }
        }
    }

    /**
     * Carica piattaforme, regioni e sorgenti per i filtri
     */
    private fun loadFiltersData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Carica piattaforme
            when (val platformsResult = repository.getPlatforms()) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(availablePlatforms = platformsResult.data) }
                }
                is NetworkResult.Error -> {
                    // Errore silenzioso, i filtri sono opzionali
                }
                is NetworkResult.Loading -> {}
            }

            // Carica regioni
            when (val regionsResult = repository.getRegions()) {
                is NetworkResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            availableRegions = regionsResult.data
                        )
                    }
                }
                is NetworkResult.Error -> {
                    // Errore silenzioso
                }
                is NetworkResult.Loading -> {}
            }
            
            // Carica sorgenti abilitate
            val enabledSources = sourceManager.getEnabledSources()
            val availableSources = enabledSources.map { source ->
                source.id to source.name
            }
            _uiState.update { 
                it.copy(
                    availableSources = availableSources,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Aggiorna la query di ricerca
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { 
            it.copy(
                query = query,
                currentPage = 1
            )
        }
    }

    /**
     * Esegue la ricerca
     */
    fun performSearch() {
        val currentState = _uiState.value
        val filters = currentState.filters.copy(query = currentState.query)

        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSearching = true, 
                    error = null,
                    currentPage = 1
                )
            }

            when (val result = repository.searchRoms(filters, page = 1)) {
                is NetworkResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            results = result.data,
                            isSearching = false,
                            hasSearched = true,
                            canLoadMore = result.data.size >= 50
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            error = result.exception.getUserMessage()
                        )
                    }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    /**
     * Carica più risultati (paginazione)
     */
    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isSearching || !currentState.canLoadMore) return

        val nextPage = currentState.currentPage + 1
        val filters = currentState.filters.copy(query = currentState.query)

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            when (val result = repository.searchRoms(filters, page = nextPage)) {
                is NetworkResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            results = state.results + result.data,
                            isSearching = false,
                            currentPage = nextPage,
                            canLoadMore = result.data.size >= 50
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isSearching = false,
                            error = result.exception.getUserMessage()
                        )
                    }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    /**
     * Aggiorna i filtri di piattaforma
     */
    fun updatePlatformFilter(platforms: List<String>) {
        _uiState.update { state ->
            state.copy(
                filters = state.filters.copy(selectedPlatforms = platforms),
                currentPage = 1
            )
        }
        performSearch()
    }

    /**
     * Aggiorna i filtri di regione
     */
    fun updateRegionFilter(regions: List<String>) {
        _uiState.update { state ->
            state.copy(
                filters = state.filters.copy(selectedRegions = regions),
                currentPage = 1
            )
        }
        performSearch()
    }

    /**
     * Aggiorna i filtri di sorgente
     */
    fun updateSourceFilter(sources: List<String>) {
        _uiState.update { state ->
            state.copy(
                filters = state.filters.copy(selectedSources = sources),
                currentPage = 1
            )
        }
        performSearch()
    }

    /**
     * Pulisce tutti i filtri
     */
    fun clearFilters() {
        android.util.Log.d("SearchViewModel", "🧹 clearFilters chiamato")
        _uiState.update { 
            it.copy(
                filters = SearchFilters(),
                currentPage = 1,
                results = emptyList(),
                canLoadMore = false,
                hasSearched = false
            )
        }
        android.util.Log.d("SearchViewModel", "🧹 Filtri resettati, stato aggiornato")
        // Non eseguire ricerca automatica quando si cancellano i filtri
    }

    /**
     * Pulisce l'errore
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
