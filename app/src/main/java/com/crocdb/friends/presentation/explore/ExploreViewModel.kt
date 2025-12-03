package com.crocdb.friends.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crocdb.friends.data.remote.NetworkResult
import com.crocdb.friends.data.remote.getUserMessage
import com.crocdb.friends.domain.model.PlatformCategory
import com.crocdb.friends.domain.model.PlatformInfo
import com.crocdb.friends.domain.model.Rom
import com.crocdb.friends.domain.repository.RomRepository
import com.crocdb.friends.presentation.common.ExploreUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel per la schermata Esplorazione
 * Gestisce categorie di piattaforme e navigazione
 */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: RomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _platformRoms = MutableStateFlow<Map<String, List<Rom>>>(emptyMap())
    val platformRoms: StateFlow<Map<String, List<Rom>>> = _platformRoms.asStateFlow()
    
    // Traccia la paginazione per ogni piattaforma
    private val _platformPages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val platformPages: StateFlow<Map<String, Int>> = _platformPages.asStateFlow()
    
    private val _canLoadMore = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val canLoadMore: StateFlow<Map<String, Boolean>> = _canLoadMore.asStateFlow()
    
    private val _isLoadingMore = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isLoadingMore: StateFlow<Map<String, Boolean>> = _isLoadingMore.asStateFlow()
    
    // Traccia quali categorie sono espanse (true = espanse, false = collassate)
    private val _expandedCategories = MutableStateFlow<Set<String>>(emptySet())
    val expandedCategories: StateFlow<Set<String>> = _expandedCategories.asStateFlow()

    init {
        loadExploreData()
    }

    /**
     * Carica piattaforme e regioni
     */
    private fun loadExploreData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Carica piattaforme
            when (val platformsResult = repository.getPlatforms()) {
                is NetworkResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            platforms = platformsResult.data,
                            isLoading = false
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = platformsResult.exception.getUserMessage()
                        )
                    }
                }
                is NetworkResult.Loading -> {}
            }

            // Carica regioni
            when (val regionsResult = repository.getRegions()) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(regions = regionsResult.data) }
                }
                is NetworkResult.Error -> {
                    // Errore silenzioso per le regioni
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    /**
     * Carica ROM per una specifica piattaforma (prima pagina)
     */
    fun loadRomsForPlatform(platformCode: String) {
        viewModelScope.launch {
            // Se già caricato, non ricaricare
            if (_platformRoms.value.containsKey(platformCode)) return@launch
            
            _uiState.update { it.copy(isLoading = true) }
            
            when (val result = repository.getRomsByPlatform(platformCode, page = 1, limit = 25)) {
                is NetworkResult.Success -> {
                    _platformRoms.update { map ->
                        map + (platformCode to result.data)
                    }
                    _platformPages.update { map ->
                        map + (platformCode to 1)
                    }
                    _canLoadMore.update { map ->
                        map + (platformCode to (result.data.size >= 25))
                    }
                    _uiState.update { it.copy(isLoading = false) }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }
    
    /**
     * Carica più ROM per una specifica piattaforma (paginazione)
     */
    fun loadMoreRomsForPlatform(platformCode: String) {
        viewModelScope.launch {
            val currentPage = _platformPages.value[platformCode] ?: 1
            val canLoad = _canLoadMore.value[platformCode] ?: false
            val isLoading = _isLoadingMore.value[platformCode] ?: false
            
            if (!canLoad || isLoading) return@launch
            
            _isLoadingMore.update { map ->
                map + (platformCode to true)
            }
            
            val nextPage = currentPage + 1
            
            when (val result = repository.getRomsByPlatform(platformCode, page = nextPage, limit = 25)) {
                is NetworkResult.Success -> {
                    _platformRoms.update { map ->
                        val existing = map[platformCode] ?: emptyList()
                        map + (platformCode to (existing + result.data))
                    }
                    _platformPages.update { map ->
                        map + (platformCode to nextPage)
                    }
                    _canLoadMore.update { map ->
                        map + (platformCode to (result.data.size >= 25))
                    }
                    _isLoadingMore.update { map ->
                        map + (platformCode to false)
                    }
                }
                is NetworkResult.Error -> {
                    _isLoadingMore.update { map ->
                        map + (platformCode to false)
                    }
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    /**
     * Ottiene le categorie di piattaforme raggruppate per brand
     */
    fun getPlatformCategories(): List<PlatformCategory> {
        val platforms = _uiState.value.platforms
        
        // Raggruppa per brand
        val platformsByBrand = platforms.groupBy { it.manufacturer ?: "Altri" }
        
        // Ordina i brand per nome (alfabetico)
        val sortedBrands = platformsByBrand.toList().sortedBy { it.first }
        
        return sortedBrands.map { (brand, brandPlatforms) ->
            PlatformCategory(
                id = brand.lowercase().replace(" ", "_"),
                name = brand,
                platforms = brandPlatforms.sortedBy { it.displayName },
                icon = "sports_esports"
            )
        }
    }

    /**
     * Cambia categoria selezionata
     */
    fun selectCategory(categoryId: String) {
        _uiState.update { it.copy(selectedCategory = categoryId) }
    }

    /**
     * Ricarica i dati
     */
    fun refresh() {
        loadExploreData()
    }

    /**
     * Pulisce l'errore
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Espande o collassa una categoria
     */
    fun toggleCategory(categoryId: String) {
        _expandedCategories.update { expanded ->
            if (expanded.contains(categoryId)) {
                expanded - categoryId
            } else {
                expanded + categoryId
            }
        }
    }
    
    /**
     * Collassa tutte le categorie
     */
    fun collapseAllCategories() {
        _expandedCategories.update { emptySet() }
    }
    
    /**
     * Espande tutte le categorie
     */
    fun expandAllCategories() {
        val allCategoryIds = getPlatformCategories().map { it.id }.toSet()
        _expandedCategories.update { allCategoryIds }
    }
}
