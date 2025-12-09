package com.tottodrillo.presentation.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tottodrillo.presentation.components.EmptyState
import com.tottodrillo.presentation.components.LoadingIndicator
import com.tottodrillo.presentation.components.RomCard
import com.tottodrillo.presentation.explore.ExploreViewModel

/**
 * Schermata che mostra le ROM per una specifica piattaforma
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformRomsScreen(
    platformCode: String,
    onNavigateBack: () -> Unit,
    onNavigateToRomDetail: (String) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val platformRoms by viewModel.platformRoms.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val gridState = rememberLazyGridState()

    val romsForPlatform = platformRoms[platformCode]
    val canLoadMoreForPlatform = canLoadMore[platformCode] ?: false
    val isLoadingMoreForPlatform = isLoadingMore[platformCode] ?: false

    // Detect when user scrolls near bottom for pagination
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem != null && 
                lastVisibleItem.index >= totalItems - 6 && 
                canLoadMoreForPlatform &&
                !isLoadingMoreForPlatform
        }
    }

    LaunchedEffect(platformCode) {
        if (romsForPlatform == null) {
            viewModel.loadRomsForPlatform(platformCode)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreRomsForPlatform(platformCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ROM - ${platformCode.uppercase()}",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && romsForPlatform == null -> {
                    // Mostra indicatore di caricamento quando si sta caricando una nuova piattaforma
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && romsForPlatform.isNullOrEmpty() -> {
                    EmptyState(
                        message = "Errore: ${uiState.error}"
                    )
                }
                romsForPlatform.isNullOrEmpty() -> {
                    EmptyState(
                        message = "Nessuna ROM trovata per ${platformCode.uppercase()}"
                    )
                }
                else -> {
                    // Calcola quali ROM sono visibili e limita a 10 immagini caricate contemporaneamente
                    val visibleItems = remember {
                        derivedStateOf {
                            val layoutInfo = gridState.layoutInfo
                            val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }
                            // Prendi i primi 10 indici visibili
                            visibleIndices.take(10).toSet()
                        }
                    }
                    
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(180.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 20.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(romsForPlatform!!.size, key = { romsForPlatform!![it].slug }) { index ->
                            val rom = romsForPlatform!![index]
                            val shouldLoad = visibleItems.value.contains(index)
                            RomCard(
                                rom = rom,
                                onClick = { onNavigateToRomDetail(rom.slug) },
                                shouldLoadImage = shouldLoad
                            )
                        }
                        
                        // Mostra indicatore di caricamento quando si caricano più risultati
                        if (isLoadingMoreForPlatform) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
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
        }
    }
}


