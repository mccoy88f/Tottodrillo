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
import androidx.compose.material.icons.filled.FilterList
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
    refreshKey: Int = 0,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    
    // Inizializza con piattaforma se specificata
    LaunchedEffect(initialPlatformCode) {
        initialPlatformCode?.let { platformCode ->
            viewModel.initializeWithPlatform(platformCode)
        }
    }
    
    // Forza il refresh quando cambia refreshKey
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            viewModel.refreshIfNeeded(refreshKey)
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
                    uiState.isLoading && !uiState.hasSearched -> {
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
                if (uiState.isSearching && (uiState.results.isEmpty() || uiState.currentPage == 1)) {
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
                text = "$totalFilters filtro${if (totalFilters == 1) "" else "i"} attivo${if (totalFilters == 1) "" else "i"}",
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
