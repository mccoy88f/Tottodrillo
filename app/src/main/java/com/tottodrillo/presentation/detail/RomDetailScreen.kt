package com.tottodrillo.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.tottodrillo.R
import coil.compose.SubcomposeAsyncImage
import com.tottodrillo.domain.model.DownloadLink
import com.tottodrillo.domain.model.Rom
import com.tottodrillo.presentation.common.RomDetailUiState
import com.tottodrillo.presentation.components.EmptyState
import com.tottodrillo.presentation.components.LoadingIndicator

/**
 * Entry point composable per la schermata di dettaglio ROM.
 * Qui colleghiamo il ViewModel alla UI.
 */
@Composable
fun RomDetailRoute(
    romSlug: String,
    onNavigateBack: () -> Unit,
    onNavigateToPlatform: (String) -> Unit,
    onRequestExtraction: (String, String, String, String) -> Unit, // archivePath, romTitle, romSlug, platformCode
    viewModel: RomDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing = uiState.isLoading && uiState.rom != null

    // Ricarica lo stato quando si rientra nella schermata
    LaunchedEffect(romSlug) {
        // Aspetta che la ROM sia caricata prima di fare il refresh
        kotlinx.coroutines.delay(300)
        val currentState = viewModel.uiState.value
        if (currentState.rom != null) {
            android.util.Log.d("RomDetailScreen", "🔄 Ricarico stato ROM al rientro nella schermata")
            viewModel.refreshRomStatus()
            
            // Dopo il refresh, verifica se ci sono estrazioni attive che non stiamo osservando
            kotlinx.coroutines.delay(500)
            val currentRom = viewModel.uiState.value.rom
            if (currentRom != null) {
                currentRom.downloadLinks.forEach { link ->
                    val linkStatus = viewModel.uiState.value.linkStatuses[link.url]
                    if (linkStatus?.first is com.tottodrillo.domain.model.DownloadStatus.Completed) {
                        val completed = linkStatus.first as com.tottodrillo.domain.model.DownloadStatus.Completed
                        val archivePath = completed.romTitle
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val activeExtractionWorkId = viewModel.getActiveExtractionWorkId(archivePath)
                            if (activeExtractionWorkId != null) {
                                android.util.Log.d("RomDetailScreen", "🔄 Trovata estrazione attiva dopo refresh per $archivePath, avvio osservazione")
                                viewModel.startObservingExtractionForLink(link, activeExtractionWorkId)
                            }
                        }
                    }
                }
            }
        }
    }
    
    
    // Controlla periodicamente se ci sono estrazioni attive (per gestire estrazioni avviate da MainActivity)
    LaunchedEffect(uiState.rom?.slug) {
        if (uiState.rom != null) {
            // Controlla ogni 1 secondo se ci sono estrazioni attive che non stiamo ancora osservando
            while (true) {
                kotlinx.coroutines.delay(1000) // Ridotto a 1 secondo per essere più reattivo
                val currentRom = viewModel.uiState.value.rom
                if (currentRom != null) {
                    // Verifica se ci sono estrazioni attive per i link scaricati
                    currentRom.downloadLinks.forEach { link ->
                        val linkStatus = viewModel.uiState.value.linkStatuses[link.url]
                        if (linkStatus?.first is com.tottodrillo.domain.model.DownloadStatus.Completed) {
                            val completed = linkStatus.first as com.tottodrillo.domain.model.DownloadStatus.Completed
                            val archivePath = completed.romTitle
                            // Verifica se c'è un'estrazione attiva per questo file
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val activeExtractionWorkId = viewModel.getActiveExtractionWorkId(archivePath)
                                if (activeExtractionWorkId != null) {
                                    android.util.Log.d("RomDetailScreen", "🔄 Trovata estrazione attiva non osservata per $archivePath, avvio osservazione")
                                    viewModel.startObservingExtractionForLink(link, activeExtractionWorkId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Controlla immediatamente quando la schermata diventa visibile se ci sono estrazioni attive o completate
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500) // Aspetta che la ROM sia caricata
        val currentRom = viewModel.uiState.value.rom
        if (currentRom != null) {
            android.util.Log.d("RomDetailScreen", "🔍 Controllo immediato estrazioni attive/completate al caricamento schermata")
            // Verifica se ci sono estrazioni attive per i link scaricati
            currentRom.downloadLinks.forEach { link ->
                val linkStatus = viewModel.uiState.value.linkStatuses[link.url]
                if (linkStatus?.first is com.tottodrillo.domain.model.DownloadStatus.Completed) {
                    val completed = linkStatus.first as com.tottodrillo.domain.model.DownloadStatus.Completed
                    val archivePath = completed.romTitle
                    // Verifica se c'è un'estrazione attiva per questo file
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val activeExtractionWorkId = viewModel.getActiveExtractionWorkId(archivePath)
                        if (activeExtractionWorkId != null) {
                            android.util.Log.d("RomDetailScreen", "🔄 Trovata estrazione attiva non osservata al caricamento per $archivePath, avvio osservazione")
                            viewModel.startObservingExtractionForLink(link, activeExtractionWorkId)
                        }
                    }
                }
            }
        }
    }
    
    // Aggiorna automaticamente lo stato quando c'è un'estrazione in corso o completata
    LaunchedEffect(uiState.extractionStatus, uiState.rom?.slug) {
        val status = uiState.extractionStatus
        if (status is com.tottodrillo.domain.model.ExtractionStatus.InProgress) {
            // Se l'estrazione è in corso, non fare nulla (l'osservazione già aggiorna l'UI)
        } else if (status is com.tottodrillo.domain.model.ExtractionStatus.Completed ||
                   status is com.tottodrillo.domain.model.ExtractionStatus.Failed) {
            // Aspetta 1 secondo e poi ricarica lo stato per assicurarsi che l'UI sia aggiornata
            kotlinx.coroutines.delay(1000)
            if (uiState.rom != null) {
                android.util.Log.d("RomDetailScreen", "🔄 Refresh automatico stato dopo estrazione completata/fallita")
                viewModel.refreshRomStatus()
            }
        }
    }
    
    // Controlla periodicamente se l'estrazione è completata ma l'UI non è aggiornata
    // (per gestire il caso in cui l'estrazione completa prima che l'osservazione parta)
    LaunchedEffect(uiState.rom?.slug) {
        if (uiState.rom != null) {
            while (true) {
                kotlinx.coroutines.delay(2000) // Controlla ogni 2 secondi
                val currentRom = viewModel.uiState.value.rom
                if (currentRom != null) {
                    // Verifica se ci sono link scaricati con estrazione Idle che potrebbero essere completati
                    currentRom.downloadLinks.forEach { link ->
                        val linkStatus = viewModel.uiState.value.linkStatuses[link.url]
                        if (linkStatus?.first is com.tottodrillo.domain.model.DownloadStatus.Completed &&
                            linkStatus.second is com.tottodrillo.domain.model.ExtractionStatus.Idle) {
                            // C'è un download completato ma estrazione Idle, verifica se l'estrazione è completata
                            android.util.Log.d("RomDetailScreen", "🔍 Verifica se estrazione completata per link ${link.url}")
                            viewModel.refreshRomStatus()
                        }
                    }
                }
            }
        }
    }

    val rom = uiState.rom

    when {
        uiState.isLoading && rom == null -> {
            LoadingIndicator()
        }
        uiState.error != null && rom == null -> {
            EmptyState(
                message = uiState.error ?: "Errore nel caricamento",
                modifier = Modifier.padding(24.dp)
            )
        }
        rom != null -> {
            RomDetailScreen(
                rom = rom,
                isFavorite = uiState.isFavorite,
                downloadStatus = uiState.downloadStatus,
                extractionStatus = uiState.extractionStatus,
                linkStatuses = uiState.linkStatuses,
                onNavigateBack = onNavigateBack,
                onNavigateToPlatform = onNavigateToPlatform,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onDownloadClick = { link -> viewModel.onDownloadButtonClick(link) },
                onExtractClick = { archivePath, romTitle ->
                    // Apri il picker per scegliere la cartella, passando anche lo slug e il platformCode
                    val platformCode = uiState.rom?.platform?.code ?: ""
                    onRequestExtraction(archivePath, romTitle, romSlug, platformCode)
                },
                onOpenExtractionFolder = { extractionPath ->
                    // Apri la cartella di estrazione
                    viewModel.openExtractionFolder(extractionPath)
                },
                onRefresh = {
                    viewModel.refreshRomDetail()
                },
                isRefreshing = isRefreshing
            )
        }
    }
}

/**
 * Schermata dettaglio ROM (UI pura)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RomDetailScreen(
    rom: Rom,
    isFavorite: Boolean,
    downloadStatus: com.tottodrillo.domain.model.DownloadStatus,
    extractionStatus: com.tottodrillo.domain.model.ExtractionStatus,
    linkStatuses: Map<String, Pair<com.tottodrillo.domain.model.DownloadStatus, com.tottodrillo.domain.model.ExtractionStatus>> = emptyMap(),
    onNavigateBack: () -> Unit,
    onNavigateToPlatform: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: (DownloadLink) -> Unit,
    onExtractClick: (String, String) -> Unit,
    onOpenExtractionFolder: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rom_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Cover image carosello (se ci sono più immagini)
            android.util.Log.d("RomDetailScreen", "🖼️ Immagini ROM: ${rom.coverUrls.size}, URLs: ${rom.coverUrls}")
            if (rom.coverUrls.size > 1) {
                // Carosello con più immagini
                ImageCarousel(
                    images = rom.coverUrls,
                    title = rom.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.33f)
                )
            } else {
                // Singola immagine
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.33f),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = rom.coverUrl,
                        contentDescription = rom.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Title
                Text(
                    text = rom.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Platform (cliccabile)
                Text(
                    text = rom.platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onNavigateToPlatform(rom.platform.code) }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Regions
                if (rom.regions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.rom_detail_region),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rom.regions.forEach { region ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = region.flagEmoji,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = region.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Download links
                if (rom.downloadLinks.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.rom_detail_download),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    rom.downloadLinks.forEach { link ->
                        // Usa lo stato specifico per questo link se disponibile, altrimenti usa lo stato generale
                        val (linkDownloadStatus, linkExtractionStatus) = linkStatuses[link.url] 
                            ?: Pair(downloadStatus, extractionStatus)
                        
                        DownloadLinkCard(
                            link = link,
                            downloadStatus = linkDownloadStatus,
                            extractionStatus = linkExtractionStatus,
                            onDownloadClick = { onDownloadClick(link) },
                            onExtractClick = onExtractClick,
                            onOpenExtractionFolder = onOpenExtractionFolder,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadLinkCard(
    link: DownloadLink,
    downloadStatus: com.tottodrillo.domain.model.DownloadStatus,
    extractionStatus: com.tottodrillo.domain.model.ExtractionStatus,
    onDownloadClick: () -> Unit,
    onExtractClick: (String, String) -> Unit,
    onOpenExtractionFolder: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = link.format.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    link.size?.let { size ->
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    link.sourceId?.let { sourceId ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = sourceId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                    // Determina il colore del pulsante in base allo stato
                    val buttonColors = when (downloadStatus) {
                        is com.tottodrillo.domain.model.DownloadStatus.Completed -> {
                            // Verde quando scaricato
                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                contentColor = androidx.compose.ui.graphics.Color.White
                            )
                        }
                        is com.tottodrillo.domain.model.DownloadStatus.Failed,
                        is com.tottodrillo.domain.model.DownloadStatus.Paused -> {
                            // Rosso quando interrotto o in errore
                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        }
                        else -> {
                            // Colore predefinito per gli altri stati
                            androidx.compose.material3.ButtonDefaults.buttonColors()
                        }
                    }
                    
                Button(
                    onClick = onDownloadClick,
                        colors = buttonColors
                ) {
                    when (downloadStatus) {
                        is com.tottodrillo.domain.model.DownloadStatus.InProgress -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(stringResource(R.string.rom_detail_download_progress, downloadStatus.progress))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.rom_detail_cancel_download),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                        }
                        is com.tottodrillo.domain.model.DownloadStatus.Completed -> {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.rom_detail_downloaded))
                        }
                        is com.tottodrillo.domain.model.DownloadStatus.Failed -> {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.retry))
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.rom_detail_download))
                        }
                    }
                }

                if (downloadStatus is com.tottodrillo.domain.model.DownloadStatus.Completed) {
                    val completed = downloadStatus as com.tottodrillo.domain.model.DownloadStatus.Completed
                        // Mostra sempre il pulsante di estrazione/copia per tutti i file scaricati
                        // Per file non-archivio verrà eseguita una copia invece di un'estrazione
                        // completed.romTitle contiene il percorso completo del file scaricato
                        when (extractionStatus) {
                            is com.tottodrillo.domain.model.ExtractionStatus.InProgress -> {
                                // Mostra progresso estrazione
                                Row(
                                    modifier = Modifier.padding(start = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                            text = stringResource(R.string.rom_detail_extraction_progress, extractionStatus.progress),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            is com.tottodrillo.domain.model.ExtractionStatus.Completed -> {
                                // Log quando viene mostrata l'icona verde
                                LaunchedEffect(extractionStatus) {
                                        Log.i("RomDetailScreen", "✅ [PASSO 4] UI: Mostrando icona verde Unarchive - Estrazione completata! Path: ${extractionStatus.extractedPath}, Files: ${extractionStatus.filesCount}")
                                }
                                    // Mostra icona estrazione verde cliccabile per aprire la cartella
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        onOpenExtractionFolder(extractionStatus.extractedPath)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Unarchive,
                                            contentDescription = stringResource(R.string.rom_detail_open_folder),
                                        modifier = Modifier.size(24.dp),
                                        tint = androidx.compose.ui.graphics.Color(0xFF4CAF50) // Verde
                                    )
                                }
                            }
                            is com.tottodrillo.domain.model.ExtractionStatus.Failed -> {
                                // Mostra icona errore (opzionale, o mostra di nuovo il pulsante)
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                            // completed.romTitle è il percorso completo del file
                                            onExtractClick(completed.romTitle, completed.filePath)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Unarchive,
                                            contentDescription = stringResource(R.string.rom_detail_retry_extraction),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            else -> {
                                    // Mostra pulsante "Estrai/Copia"
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                            // completed.romTitle è il percorso completo del file
                                            onExtractClick(completed.romTitle, completed.filePath)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Unarchive,
                                            contentDescription = if (isSupportedArchiveFile(completed.romTitle)) 
                                                stringResource(R.string.rom_detail_extract_archive) 
                                            else 
                                                stringResource(R.string.rom_detail_copy_file),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Mostra progresso download sotto i pulsanti quando è in corso
            if (downloadStatus is com.tottodrillo.domain.model.DownloadStatus.InProgress) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = downloadStatus.progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.rom_detail_download_progress, downloadStatus.progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatBytes(downloadStatus.bytesDownloaded) + " / " + formatBytes(downloadStatus.totalBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Verifica se un file ha un'estensione di archivio supportata (.zip, .rar, .7z)
 */
private fun isSupportedArchiveFile(filePath: String): Boolean {
    val fileName = filePath.lowercase()
    return fileName.endsWith(".zip") || 
           fileName.endsWith(".rar") || 
           fileName.endsWith(".7z")
}

/**
 * Formatta bytes in formato leggibile
 */
/**
 * Carosello di immagini per la ROM detail
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageCarousel(
    images: List<String>,
    title: String,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("RomDetailScreen", "🎠 ImageCarousel: ${images.size} immagini")
    val pagerState = rememberPagerState(pageCount = { images.size })
    val scope = rememberCoroutineScope()
    
    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val imageUrl = images.getOrNull(page)
            android.util.Log.d("RomDetailScreen", "🎠 Caricamento pagina $page/${images.size}: $imageUrl")
            if (imageUrl == null) {
                android.util.Log.w("RomDetailScreen", "⚠️ Immagine null per pagina $page")
            }
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = stringResource(
                    R.string.rom_detail_image_item,
                    title,
                    page + 1
                ),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    android.util.Log.e("RomDetailScreen", "❌ Errore caricamento immagine pagina $page: $imageUrl")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
        
        // Indicatori di pagina
        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(images.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (pagerState.currentPage == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}