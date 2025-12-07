package com.tottodrillo.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tottodrillo.presentation.components.FilterChip

/**
 * Bottom sheet per filtri di ricerca
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersBottomSheet(
    onDismiss: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Sincronizza lo stato locale con lo stato del ViewModel
    var selectedPlatforms by remember(uiState.filters.selectedPlatforms) {
        mutableStateOf(uiState.filters.selectedPlatforms)
    }
    var selectedRegions by remember(uiState.filters.selectedRegions) {
        mutableStateOf(uiState.filters.selectedRegions)
    }
    var selectedSources by remember(uiState.filters.selectedSources) {
        mutableStateOf(uiState.filters.selectedSources)
    }
    
    // Aggiorna lo stato locale quando cambia lo stato del ViewModel (es. quando si chiama clearFilters)
    androidx.compose.runtime.LaunchedEffect(uiState.filters.selectedPlatforms, uiState.filters.selectedRegions, uiState.filters.selectedSources) {
        selectedPlatforms = uiState.filters.selectedPlatforms
        selectedRegions = uiState.filters.selectedRegions
        selectedSources = uiState.filters.selectedSources
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        FiltersContent(
            availablePlatforms = uiState.availablePlatforms
                .map { it.code to it.displayName }
                .sortedBy { it.second }, // Ordina alfabeticamente per nome
            availableRegions = uiState.availableRegions
                .map { it.code to it.displayName }
                .sortedBy { it.second }, // Ordina alfabeticamente per nome
            availableSources = uiState.availableSources
                .sortedBy { it.second }, // Ordina alfabeticamente per nome
            selectedPlatforms = selectedPlatforms,
            selectedRegions = selectedRegions,
            selectedSources = selectedSources,
            onPlatformToggle = { platform ->
                selectedPlatforms = if (platform in selectedPlatforms) {
                    selectedPlatforms - platform
                } else {
                    selectedPlatforms + platform
                }
            },
            onRegionToggle = { region ->
                selectedRegions = if (region in selectedRegions) {
                    selectedRegions - region
                } else {
                    selectedRegions + region
                }
            },
            onSourceToggle = { source ->
                selectedSources = if (source in selectedSources) {
                    selectedSources - source
                } else {
                    selectedSources + source
                }
            },
            onClearAll = {
                selectedPlatforms = emptyList()
                selectedRegions = emptyList()
                selectedSources = emptyList()
            },
            onApply = {
                viewModel.updatePlatformFilter(selectedPlatforms)
                viewModel.updateRegionFilter(selectedRegions)
                viewModel.updateSourceFilter(selectedSources)
                onDismiss()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersContent(
    availablePlatforms: List<Pair<String, String>>,
    availableRegions: List<Pair<String, String>>,
    availableSources: List<Pair<String, String>>,
    selectedPlatforms: List<String>,
    selectedRegions: List<String>,
    selectedSources: List<String>,
    onPlatformToggle: (String) -> Unit,
    onRegionToggle: (String) -> Unit,
    onSourceToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Filtri di Ricerca",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                onClick = onClearAll,
                enabled = selectedPlatforms.isNotEmpty() || selectedRegions.isNotEmpty() || selectedSources.isNotEmpty()
            ) {
                Text("Cancella tutto")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Piattaforme section
        if (availablePlatforms.isNotEmpty()) {
            FilterSection(
                title = androidx.compose.ui.res.stringResource(com.tottodrillo.R.string.search_platforms),
                items = availablePlatforms,
                selectedItems = selectedPlatforms,
                onItemToggle = onPlatformToggle
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Regioni section
        if (availableRegions.isNotEmpty()) {
            FilterSection(
                title = androidx.compose.ui.res.stringResource(com.tottodrillo.R.string.search_regions),
                items = availableRegions,
                selectedItems = selectedRegions,
                onItemToggle = onRegionToggle
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sorgenti section
        if (availableSources.isNotEmpty()) {
            FilterSection(
                title = androidx.compose.ui.res.stringResource(com.tottodrillo.R.string.search_sources),
                items = availableSources,
                selectedItems = selectedSources,
                onItemToggle = onSourceToggle
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Actions
        Divider()

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1f)
            ) {
                val count = selectedPlatforms.size + selectedRegions.size + selectedSources.size
                Text(
                    text = if (count > 0) {
                        "Applica ($count)"
                    } else {
                        "Applica"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    items: List<Pair<String, String>>,
    selectedItems: List<String>,
    onItemToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { (code, name) ->
                FilterChip(
                    label = name, // Mostra il nome invece del codice
                    selected = code in selectedItems,
                    onClick = { onItemToggle(code) } // Usa comunque il codice per la logica
                )
            }
        }

        // Selected count
        if (selectedItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${selectedItems.size} selezionat${if (selectedItems.size == 1) "o" else "i"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
