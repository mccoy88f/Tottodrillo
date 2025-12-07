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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    private var lastRefreshKey: Int = 0
    
    // Traccia i job attivi per poterli cancellare
    private var currentSearchJob: Job? = null
    private var currentLoadMoreJob: Job? = null

    init {
        loadFiltersData()
        observeSearchQuery()
    }
    
    /**
     * Forza il refresh quando cambia il refreshKey
     */
    fun refreshIfNeeded(refreshKey: Int) {
        if (refreshKey != lastRefreshKey) {
            lastRefreshKey = refreshKey
            loadFiltersData()
            // Se c'è una ricerca attiva, ricarica anche i risultati
            if (_searchQuery.value.isNotEmpty() || _uiState.value.filters.selectedPlatforms.isNotEmpty()) {
                performSearch()
            }
        }
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
     * Inizializza con una query di ricerca
     */
    fun initializeWithQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { state ->
            state.copy(
                query = query,
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
     * Carica piattaforme e sorgenti per i filtri
     * Le regioni vengono estratte dai risultati della ricerca
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
     * Estrae le regioni disponibili dai risultati della ricerca
     */
    private fun extractRegionsFromResults(roms: List<com.tottodrillo.domain.model.Rom>): List<com.tottodrillo.domain.model.RegionInfo> {
        // Ottieni tutte le regioni uniche dai risultati
        val regionsFromResults = roms
            .flatMap { it.regions }
            .distinctBy { it.code }
            .sortedBy { it.displayName }
        
        // Se non ci sono regioni nei risultati, usa la lista predefinita
        if (regionsFromResults.isEmpty()) {
            return getDefaultRegions()
        }
        
        return regionsFromResults
    }
    
    /**
     * Restituisce la lista predefinita di regioni
     */
    private fun getDefaultRegions(): List<com.tottodrillo.domain.model.RegionInfo> {
        return listOf(
            com.tottodrillo.domain.model.RegionInfo.fromCode("US"),
            com.tottodrillo.domain.model.RegionInfo.fromCode("EU"),
            com.tottodrillo.domain.model.RegionInfo.fromCode("JP"),
            com.tottodrillo.domain.model.RegionInfo.fromCode("WW")
        )
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
        // Cancella la ricerca precedente se è ancora in corso
        currentSearchJob?.cancel()
        currentLoadMoreJob?.cancel()
        
        val currentState = _uiState.value
        val filters = currentState.filters.copy(query = currentState.query)

        currentSearchJob = viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSearching = true, 
                    error = null,
                    currentPage = 1
                )
            }

            // Verifica se il job è stato cancellato prima di procedere
            if (!isActive) return@launch
            
            when (val result = repository.searchRoms(filters, page = 1)) {
                is NetworkResult.Success -> {
                    // Verifica di nuovo se il job è ancora attivo prima di aggiornare lo stato
                    if (!isActive) return@launch
                    
                    // Estrai le regioni disponibili dai risultati
                    val availableRegions = extractRegionsFromResults(result.data)
                    
                    _uiState.update { state ->
                        state.copy(
                            results = result.data,
                            availableRegions = availableRegions,
                            isSearching = false,
                            hasSearched = true,
                            canLoadMore = result.data.size >= 50
                        )
                    }
                }
                is NetworkResult.Error -> {
                    // Verifica se il job è ancora attivo prima di aggiornare lo stato
                    if (!isActive) return@launch
                    
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

        // Cancella il loadMore precedente se è ancora in corso
        currentLoadMoreJob?.cancel()

        val nextPage = currentState.currentPage + 1
        val filters = currentState.filters.copy(query = currentState.query)

        currentLoadMoreJob = viewModelScope.launch {
            // Verifica se il job è stato cancellato prima di procedere
            if (!isActive) return@launch
            
            _uiState.update { it.copy(isSearching = true) }

            when (val result = repository.searchRoms(filters, page = nextPage)) {
                is NetworkResult.Success -> {
                    // Verifica di nuovo se il job è ancora attivo prima di aggiornare lo stato
                    if (!isActive) return@launch
                    
                    _uiState.update { state ->
                        val allResults = state.results + result.data
                        // Aggiorna le regioni disponibili con i nuovi risultati
                        val availableRegions = extractRegionsFromResults(allResults)
                        
                        state.copy(
                            results = allResults,
                            availableRegions = availableRegions,
                            isSearching = false,
                            currentPage = nextPage,
                            canLoadMore = result.data.size >= 50
                        )
                    }
                }
                is NetworkResult.Error -> {
                    // Verifica se il job è ancora attivo prima di aggiornare lo stato
                    if (!isActive) return@launch
                    
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
        _uiState.update { 
            it.copy(
                filters = SearchFilters(),
                currentPage = 1,
                results = emptyList(),
                canLoadMore = false,
                hasSearched = false
            )
        }
        // Non eseguire ricerca automatica quando si cancellano i filtri
    }

    /**
     * Pulisce l'errore
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
