package com.tottodrillo.presentation.common

import com.tottodrillo.domain.model.DownloadStatus
import com.tottodrillo.domain.model.ExtractionStatus
import com.tottodrillo.domain.model.PlatformInfo
import com.tottodrillo.domain.model.RegionInfo
import com.tottodrillo.domain.model.Rom
import com.tottodrillo.domain.model.SearchFilters

/**
 * Stato UI generico
 */
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

/**
 * Stato della schermata Home
 */
data class HomeUiState(
    val featuredRoms: List<Rom> = emptyList(),
    val favoriteRoms: List<Rom> = emptyList(),
    val recentRoms: List<Rom> = emptyList(),
    val recentPlatforms: List<PlatformInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingFeatured: Boolean = false,
    val isLoadingFavorites: Boolean = false,
    val isLoadingRecent: Boolean = false,
    val error: String? = null
)

/**
 * Stato della schermata Esplorazione
 */
data class ExploreUiState(
    val platforms: List<PlatformInfo> = emptyList(),
    val regions: List<RegionInfo> = emptyList(),
    val selectedCategory: String = "all",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Stato della schermata Ricerca
 */
data class SearchUiState(
    val query: String = "",
    val results: List<Rom> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    val availablePlatforms: List<PlatformInfo> = emptyList(),
    val availableRegions: List<RegionInfo> = emptyList(),
    val availableSources: List<Pair<String, String>> = emptyList(), // sourceId -> sourceName
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = false
) {
    val showEmptyState: Boolean
        get() = hasSearched && results.isEmpty() && !isLoading
    
    val showFilters: Boolean
        get() = availablePlatforms.isNotEmpty() || availableRegions.isNotEmpty() || availableSources.isNotEmpty()
}

/**
 * Stato della schermata Dettaglio ROM
 */
data class RomDetailUiState(
    val rom: Rom? = null,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.Idle,
    val extractionStatus: ExtractionStatus = ExtractionStatus.Idle,
    // Mappa di stati per ogni link (URL -> Pair<DownloadStatus, ExtractionStatus>)
    val linkStatuses: Map<String, Pair<DownloadStatus, ExtractionStatus>> = emptyMap(),
    // WebView headless per gestire download con JavaScript/countdown
    val showWebView: Boolean = false,
    val webViewUrl: String? = null,
    val webViewLink: com.tottodrillo.domain.model.DownloadLink? = null
)

/**
 * Stato della schermata Preferiti
 */
data class FavoritesUiState(
    val favorites: List<Rom> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val isEmpty: Boolean get() = favorites.isEmpty() && !isLoading
}

/**
 * Eventi UI generici
 */
sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    data class ShowToast(val message: String) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    data object NavigateBack : UiEvent()
}
