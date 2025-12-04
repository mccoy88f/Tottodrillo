package com.tottodrillo.presentation.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.tottodrillo.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tottodrillo.domain.model.PlatformInfo
import com.tottodrillo.presentation.common.HomeUiState
import com.tottodrillo.presentation.components.EmptyState
import com.tottodrillo.presentation.components.LoadingIndicator
import com.tottodrillo.presentation.components.RomCard

/**
 * Schermata Home principale
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToExplore: () -> Unit,
    onNavigateToPlatform: (String) -> Unit,
    onNavigateToRomDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Ricarica i preferiti e le ROM recenti quando si torna alla home
    LaunchedEffect(Unit) {
        viewModel.loadFavoriteRoms()
        viewModel.loadRecentRoms()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = com.tottodrillo.R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = "Tottodrillo",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.recentPlatforms.isEmpty() -> {
                LoadingIndicator(modifier = Modifier.padding(padding))
            }
            uiState.error != null && uiState.recentPlatforms.isEmpty() -> {
                EmptyState(
                    message = uiState.error ?: stringResource(R.string.error_loading),
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                HomeContent(
                    uiState = uiState,
                    onNavigateToExplore = onNavigateToExplore,
                    onNavigateToPlatform = onNavigateToPlatform,
                    onNavigateToRomDetail = onNavigateToRomDetail,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onNavigateToExplore: () -> Unit,
    onNavigateToPlatform: (String) -> Unit,
    onNavigateToRomDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Welcome section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Piattaforme popolari
        if (uiState.recentPlatforms.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.home_popular_platforms),
                onSeeAllClick = onNavigateToExplore
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.recentPlatforms) { platform ->
                    PlatformCard(
                        platform = platform,
                        onClick = { onNavigateToPlatform(platform.code) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ROM in evidenza
        if (uiState.featuredRoms.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.home_featured))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.featuredRoms) { rom ->
                    RomCard(
                        rom = rom,
                        onClick = { onNavigateToRomDetail(rom.slug) },
                        modifier = Modifier.width(180.dp) // Larghezza massima come nella ricerca
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ROM recenti
        if (uiState.recentRoms.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.home_recent))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.recentRoms) { rom ->
                    RomCard(
                        rom = rom,
                        onClick = { onNavigateToRomDetail(rom.slug) },
                        modifier = Modifier.width(180.dp) // Larghezza massima come nella ricerca
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ROM preferiti
        if (uiState.favoriteRoms.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.home_favorites))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.favoriteRoms) { rom ->
                    RomCard(
                        rom = rom,
                        onClick = { onNavigateToRomDetail(rom.slug) },
                        modifier = Modifier.width(180.dp) // Larghezza massima come nella ricerca
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (onSeeAllClick != null) {
            Text(
                text = stringResource(R.string.see_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onSeeAllClick)
            )
        }
    }
}

@Composable
private fun PlatformCard(
    platform: PlatformInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(width = 140.dp, height = 100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    // Mostra l'immagine SVG se disponibile, altrimenti icona generica
                    if (platform.imagePath != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/${platform.imagePath}") // URI per caricare da assets
                                .build(),
                            contentDescription = platform.displayName,
                            modifier = Modifier.size(40.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = platform.displayName.take(12),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
