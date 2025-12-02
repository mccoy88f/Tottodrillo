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
     * Ottiene le categorie di piattaforme
     */
    fun getPlatformCategories(): List<PlatformCategory> {
        val platforms = _uiState.value.platforms
        
        return listOf(
            PlatformCategory(
                id = "nintendo",
                name = "Nintendo",
                platforms = platforms.filter { 
                    it.code.uppercase() in listOf("NES", "SNES", "N64", "GC", "WII", "WIIU", "SWITCH")
                },
                icon = "sports_esports"
            ),
            PlatformCategory(
                id = "nintendo_handheld",
                name = "Nintendo Handheld",
                platforms = platforms.filter { 
                    it.code.uppercase() in listOf("GB", "GBC", "GBA", "NDS", "3DS")
                },
                icon = "videogame_asset"
            ),
            PlatformCategory(
                id = "playstation",
                name = "PlayStation",
                platforms = platforms.filter { 
                    it.code.uppercase() in listOf("PS1", "PS2", "PS3", "PS4", "PS5", "PSP", "PSVITA")
                },
                icon = "sports_esports"
            ),
            PlatformCategory(
                id = "sega",
                name = "Sega",
                platforms = platforms.filter { 
                    it.code.uppercase() in listOf("SMS", "SMD", "SATURN", "DC", "GG")
                },
                icon = "sports_esports"
            ),
            PlatformCategory(
                id = "xbox",
                name = "Xbox",
                platforms = platforms.filter { 
                    it.code.uppercase() in listOf("XBOX", "XBOX360", "XBOXONE")
                },
                icon = "sports_esports"
            ),
            PlatformCategory(
                id = "other",
                name = "Altri",
                platforms = platforms.filter { 
                    it.code.uppercase() !in listOf(
                        "NES", "SNES", "N64", "GC", "WII", "WIIU", "SWITCH",
                        "GB", "GBC", "GBA", "NDS", "3DS",
                        "PS1", "PS2", "PS3", "PS4", "PS5", "PSP", "PSVITA",
                        "SMS", "SMD", "SATURN", "DC", "GG",
                        "XBOX", "XBOX360", "XBOXONE"
                    )
                },
                icon = "devices_other"
            )
        ).filter { it.platforms.isNotEmpty() }
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
}
