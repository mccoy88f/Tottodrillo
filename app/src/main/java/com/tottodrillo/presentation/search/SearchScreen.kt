package com.tottodrillo.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.tottodrillo.R
import com.tottodrillo.presentation.components.EmptyState
import com.tottodrillo.presentation.components.LoadingIndicator
import com.tottodrillo.presentation.components.RomCard

/**
 * Schermata Ricerca con filtri
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRomDetail: (String) -> Unit,
    onShowFilters: () -> Unit,
    initialPlatformCode: String? = null,
    initialQuery: String? = null,
    refreshKey: Int = 0,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    // Usa rememberSaveable per preservare lo stato della griglia quando si naviga a una ROM
    // Salva manualmente firstVisibleItemIndex e firstVisibleItemScrollOffset
    var savedScrollIndex by rememberSaveable { mutableStateOf(0) }
    var savedScrollOffset by rememberSaveable { mutableStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )
    
    // Salva lo stato della griglia quando cambia
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        savedScrollIndex = gridState.firstVisibleItemIndex
        savedScrollOffset = gridState.firstVisibleItemScrollOffset
    }
    
    // Reset stato quando si naviga alla schermata senza parametri (dalla home)
    // Solo se lo stato ha ancora risultati o haSearched = true
    LaunchedEffect(initialPlatformCode, initialQuery) {
        if (initialPlatformCode == null && initialQuery == null) {
            val currentState = viewModel.uiState.value
            // NON resettare se ci sono già risultati (significa che stiamo tornando indietro da una ROM)
            // Reset solo se siamo appena arrivati dalla home (prima navigazione) e non ci sono risultati
            if (!currentState.hasSearched && currentState.results.isEmpty()) {
                android.util.Log.d("SearchScreen", "🔄 Reset stato: navigazione iniziale dalla home senza parametri")
                viewModel.resetState()
            } else {
                android.util.Log.d("SearchScreen", "✅ Preservo stato: tornando indietro da ROM (hasSearched=${currentState.hasSearched}, results=${currentState.results.size})")
            }
        }
    }
    
    // Inizializza con piattaforma se specificata
    LaunchedEffect(initialPlatformCode) {
        initialPlatformCode?.let { platformCode ->
            viewModel.initializeWithPlatform(platformCode)
        }
    }
    
    // Inizializza con query se specificata
    LaunchedEffect(initialQuery) {
        initialQuery?.let { query ->
            viewModel.initializeWithQuery(query)
        }
    }
    
    // Forza il refresh quando cambia refreshKey
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            viewModel.refreshIfNeeded(refreshKey)
        }
    }
    
    // Traccia se è una nuova ricerca o se stiamo tornando indietro
    var previousResultsSize by rememberSaveable { mutableStateOf(0) }
    var previousQuery by rememberSaveable { mutableStateOf("") }
    var previousFiltersHash by rememberSaveable { mutableStateOf(0) }
    
    // Calcola hash dei filtri per rilevare cambiamenti
    val currentFiltersHash = remember(uiState.filters.selectedPlatforms, uiState.filters.selectedRegions, uiState.filters.selectedSources, uiState.filters.selectedFormats) {
        (uiState.filters.selectedPlatforms.toString() + 
         uiState.filters.selectedRegions.toString() + 
         uiState.filters.selectedSources.toString() + 
         uiState.filters.selectedFormats.toString()).hashCode()
    }
    
    // Fai tornare la lista in alto quando cambiano i filtri (solo se è una nuova ricerca)
    LaunchedEffect(currentFiltersHash) {
        // Scroll in alto solo se i filtri sono cambiati (nuova ricerca)
        if (currentFiltersHash != previousFiltersHash && uiState.hasSearched && gridState.firstVisibleItemIndex > 0) {
            kotlinx.coroutines.delay(50) // Piccolo delay per evitare conflitti con il rendering
            gridState.animateScrollToItem(0)
        }
        previousFiltersHash = currentFiltersHash
    }
    
    // Fai tornare la lista in alto quando cambiano i risultati (solo se è una nuova ricerca)
    LaunchedEffect(uiState.results.size, uiState.query) {
        // Scroll in alto solo se è una nuova ricerca:
        // - Risultati sono aumentati (nuova ricerca o paginazione)
        // - Query è cambiata E non è vuota (nuova ricerca, non cancellazione)
        // NON scrollare se i risultati sono diminuiti (significa che stiamo tornando indietro)
        val isNewSearch = (uiState.results.size > previousResultsSize) || 
                         (uiState.query != previousQuery && uiState.query.isNotEmpty() && previousQuery.isNotEmpty())
        
        if (isNewSearch && uiState.hasSearched && gridState.firstVisibleItemIndex > 0) {
            kotlinx.coroutines.delay(50) // Piccolo delay per evitare conflitti con il rendering
            gridState.animateScrollToItem(0)
        }
        
        // Aggiorna i valori precedenti solo se non stiamo tornando indietro
        if (uiState.results.size >= previousResultsSize || uiState.query.isNotEmpty()) {
            previousResultsSize = uiState.results.size
            previousQuery = uiState.query
        }
    }

    // Detect when user scrolls near bottom for pagination
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem != null && 
                lastVisibleItem.index >= totalItems - 6 && 
                uiState.canLoadMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isSearching) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_search)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.showFilters) {
                        IconButton(onClick = onShowFilters) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.search_filters)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            SearchBar(
                query = uiState.query,
                onQueryChange = viewModel::updateSearchQuery,
                onClear = { viewModel.updateSearchQuery("") },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            // Cronologia ricerche (mostra quando non si sta cercando e non ci sono filtri attivi)
            // Appare quando:
            // 1. Si apre la schermata di ricerca dalla home (hasSearched = false)
            // 2. Si torna indietro e si rientra (mostra sempre se non ci sono filtri attivi)
            // 3. Si cancella la ricerca (results.isEmpty() e query vuota)
            val shouldShowHistory = !uiState.isSearching && 
                searchHistory.isNotEmpty() && 
                uiState.query.isEmpty() && 
                !uiState.filters.hasActiveFilters()
            
            android.util.Log.d("SearchScreen", "🔍 Condizioni cronologia: isSearching=${uiState.isSearching}, historySize=${searchHistory.size}, query='${uiState.query}', hasFilters=${uiState.filters.hasActiveFilters()}, shouldShow=$shouldShowHistory")
            
            if (shouldShowHistory) {
                RecentSearchesList(
                    searches = searchHistory,
                    availablePlatforms = uiState.availablePlatforms,
                    availableRegions = uiState.availableRegions,
                    availableSources = uiState.availableSources,
                    onSearchClick = viewModel::restoreSearch,
                    onDeleteClick = viewModel::removeSearchFromHistory,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // Active filters indicator
            if (uiState.filters.hasActiveFilters()) {
                ActiveFiltersBar(
                    platformCount = uiState.filters.selectedPlatforms.size,
                    regionCount = uiState.filters.selectedRegions.size,
                    sourceCount = uiState.filters.selectedSources.size,
                    resultsCount = uiState.results.size,
                    canLoadMore = uiState.canLoadMore,
                    onClearAll = viewModel::clearFilters,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // Mostra LoadingIndicator solo se non c'è una ricerca in corso
                    uiState.isLoading && !uiState.hasSearched && !uiState.isSearching -> {
                        LoadingIndicator()
                    }
                    uiState.showEmptyState -> {
                        EmptyState(
                            message = if (uiState.query.isEmpty()) {
                                stringResource(R.string.search_hint)
                            } else {
                                stringResource(R.string.search_no_results, uiState.query)
                            }
                        )
                    }
                    uiState.error != null && uiState.results.isEmpty() -> {
                        EmptyState(message = uiState.error ?: stringResource(R.string.error_loading))
                    }
                    else -> {
                        SearchResults(
                            results = uiState.results,
                            isLoadingMore = uiState.isSearching && uiState.results.isNotEmpty(),
                            onRomClick = onNavigateToRomDetail,
                            gridState = gridState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Mostra indicatore di caricamento quando si sta facendo una ricerca
                // Mostra anche quando si applica un filtro e ci sono già risultati (sostituisce i risultati)
                // Non mostrare se isLoading è true (per evitare doppio indicatore)
                if (uiState.isSearching && (uiState.results.isEmpty() || uiState.currentPage == 1) && !uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun ActiveFiltersBar(
    platformCount: Int,
    regionCount: Int,
    sourceCount: Int,
    resultsCount: Int,
    canLoadMore: Boolean,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalFilters = platformCount + regionCount + sourceCount
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClearAll() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (totalFilters == 1) {
                    "$totalFilters filtro attivo"
                } else {
                    "$totalFilters filtri attivi"
                },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
        }

        Text(
            text = if (canLoadMore && resultsCount >= 50) {
                "$resultsCount ROMs+"
            } else if (resultsCount == 1) {
                "$resultsCount ROM"
            } else {
                "$resultsCount ROMs"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SearchResults(
    results: List<com.tottodrillo.domain.model.Rom>,
    isLoadingMore: Boolean,
    onRomClick: (String) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier
) {
    // Calcola quali ROM sono visibili e limita a 10 immagini caricate contemporaneamente
    val visibleItems = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }
            // Prendi i primi 10 indici visibili
            visibleIndices.take(10).toSet()
        }
    }
    
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 20.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(results.size, key = { results[it].slug }) { index ->
                val rom = results[index]
                val shouldLoad = visibleItems.value.contains(index)
                RomCard(
                    rom = rom,
                    onClick = { onRomClick(rom.slug) },
                    shouldLoadImage = shouldLoad
                )
            }

            // Loading more indicator
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchesList(
    searches: List<com.tottodrillo.data.repository.SavedSearch>,
    availablePlatforms: List<com.tottodrillo.domain.model.PlatformInfo>,
    availableRegions: List<com.tottodrillo.domain.model.RegionInfo>,
    availableSources: List<Pair<String, String>>,
    onSearchClick: (com.tottodrillo.data.repository.SavedSearch) -> Unit,
    onDeleteClick: (com.tottodrillo.data.repository.SavedSearch) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.search_recent_searches),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        searches.forEach { search ->
            RecentSearchItem(
                search = search,
                availablePlatforms = availablePlatforms,
                availableRegions = availableRegions,
                availableSources = availableSources,
                onClick = { onSearchClick(search) },
                onDeleteClick = { onDeleteClick(search) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun RecentSearchItem(
    search: com.tottodrillo.data.repository.SavedSearch,
    availablePlatforms: List<com.tottodrillo.domain.model.PlatformInfo>,
    availableRegions: List<com.tottodrillo.domain.model.RegionInfo>,
    availableSources: List<Pair<String, String>>,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calcola la descrizione dei filtri con i nomi effettivi
    val filtersDesc = remember(search.filters, availablePlatforms, availableRegions, availableSources) {
        val parts = mutableListOf<String>()
        
        // Piattaforme
        if (search.filters.selectedPlatforms.isNotEmpty()) {
            val platformNames = search.filters.selectedPlatforms.mapNotNull { code ->
                availablePlatforms.find { it.code == code }?.displayName ?: code.uppercase()
            }
            if (platformNames.isNotEmpty()) {
                parts.add(platformNames.joinToString(", "))
            }
        }
        
        // Regioni
        if (search.filters.selectedRegions.isNotEmpty()) {
            val regionNames = search.filters.selectedRegions.mapNotNull { code ->
                com.tottodrillo.domain.model.RegionInfo.fromCode(code).displayName
            }
            if (regionNames.isNotEmpty()) {
                parts.add(regionNames.joinToString(", "))
            }
        }
        
        // Sorgenti
        if (search.filters.selectedSources.isNotEmpty()) {
            val sourceNames = search.filters.selectedSources.mapNotNull { sourceId ->
                availableSources.find { it.first == sourceId }?.second ?: sourceId
            }
            if (sourceNames.isNotEmpty()) {
                parts.add(sourceNames.joinToString(", "))
            }
        }
        
        // Formati
        if (search.filters.selectedFormats.isNotEmpty()) {
            parts.add(search.filters.selectedFormats.joinToString(", ").uppercase())
        }
        
        if (parts.isNotEmpty()) {
            " • ${parts.joinToString(" • ")}"
        } else {
            ""
        }
    }
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            if (search.query.isNotEmpty()) {
                Text(
                    text = search.query,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = stringResource(R.string.search_filters),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (filtersDesc.isNotEmpty()) {
                Text(
                    text = filtersDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
