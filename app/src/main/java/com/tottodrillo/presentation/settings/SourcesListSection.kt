package com.tottodrillo.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tottodrillo.domain.manager.SourceManager
import com.tottodrillo.domain.manager.SourceUpdateManager
import com.tottodrillo.domain.model.Source
import com.tottodrillo.domain.model.SourceUpdate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tottodrillo.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.NotificationImportant

/**
 * Sezione lista sorgenti nelle impostazioni
 */
@Composable
fun SourcesListSection(
    sourceManager: SourceManager,
    sourceUpdateManager: SourceUpdateManager? = null,
    onSourcesChanged: () -> Unit = {},
    onUninstallSource: (String) -> Unit = {},
    onUpdateSource: () -> Unit = {},
    onInstallDefaultSources: () -> Unit = {},
    onUpdateSourceFromUrl: (String) -> Unit = {},
    externalRefreshTrigger: Int = 0
) {
    val manager = sourceManager
    
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var sourceConfigs by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var availableUpdates by remember { mutableStateOf<List<SourceUpdate>>(emptyList()) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Ricarica le sorgenti quando cambia externalRefreshTrigger o quando viene montato il composable
    LaunchedEffect(externalRefreshTrigger) {
        sources = manager.getInstalledSources()
        // Carica lo stato abilitato/disabilitato
        val configs = manager.loadInstalledConfigs()
        sourceConfigs = configs.associate { it.sourceId to it.isEnabled }
        
        // Verifica aggiornamenti se SourceUpdateManager è disponibile
        if (sourceUpdateManager != null && sources.isNotEmpty()) {
            isCheckingUpdates = true
            try {
                availableUpdates = sourceUpdateManager.checkForUpdates(sources)
            } catch (e: Exception) {
                android.util.Log.e("SourcesListSection", "Errore verifica aggiornamenti", e)
            } finally {
                isCheckingUpdates = false
            }
        }
    }
    
    if (sources.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.sources_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pulsante per installare sorgenti predefinite
            var isInstalling by remember { mutableStateOf(false) }
            
            // Reset dello stato quando le sorgenti vengono ricaricate o dopo un timeout
            LaunchedEffect(externalRefreshTrigger, isInstalling) {
                if (isInstalling) {
                    // Se ci sono sorgenti dopo l'installazione, resetta subito
                    if (sources.isNotEmpty()) {
                        isInstalling = false
                    } else {
                        // Altrimenti aspetta un po' e resetta (in caso di errore o timeout)
                        delay(5000) // 5 secondi di timeout
                        isInstalling = false
                    }
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !isInstalling,
                        onClick = {
                            isInstalling = true
                            onInstallDefaultSources()
                        }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sources_installing_default),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sources_install_default),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    } else {
        // Pulsante "Verifica aggiornamenti" se SourceUpdateManager è disponibile
        if (sourceUpdateManager != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sources_check_updates),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (availableUpdates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.sources_updates_available, availableUpdates.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdates = true
                                    try {
                                        availableUpdates = sourceUpdateManager.checkForUpdates(sources)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SourcesListSection", "Errore verifica aggiornamenti", e)
                                    } finally {
                                        isCheckingUpdates = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.sources_check_updates)
                            )
                        }
                    }
                }
            }
        }
        
        sources.forEach { source ->
            val isEnabled = sourceConfigs[source.id] ?: true
            val update = availableUpdates.find { it.sourceId == source.id }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (update != null) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (update != null) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationImportant,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (source.description != null) {
                                Text(
                                    text = source.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.sources_version_label, source.version),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (update != null) {
                                    Text(
                                        text = "→ ${update.availableVersion}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    manager.setSourceEnabled(source.id, enabled)
                                    // Aspetta che il salvataggio sia completato
                                    kotlinx.coroutines.delay(500)
                                    // Ricarica lo stato
                                    val configs = manager.loadInstalledConfigs()
                                    sourceConfigs = configs.associate { it.sourceId to it.isEnabled }
                                    // Notifica che le sorgenti sono cambiate
                                    try {
                                        onSourcesChanged()
                                    } catch (e: Exception) {
                                        android.util.Log.e("SourcesListSection", "Errore chiamando onSourcesChanged(): ${e.message}", e)
                                    }
                                }
                            }
                        )
                    }
                    
                    // Mostra changelog se disponibile
                    if (update != null && !update.changelog.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sources_changelog),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = update.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Pulsanti di azione
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Pulsante aggiorna automatico (se c'è un aggiornamento disponibile)
                        if (update != null) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        onUpdateSourceFromUrl(update.downloadUrl)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = stringResource(R.string.sources_update_auto),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.sources_update_auto))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        // Pulsante aggiorna manuale
                        TextButton(
                            onClick = {
                                onUpdateSource()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = stringResource(R.string.sources_update),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.sources_update))
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Pulsante disinstalla
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val success = manager.uninstallSource(source.id)
                                    if (success) {
                                        onUninstallSource(source.id)
                                        // Ricarica le sorgenti
                                        sources = manager.getInstalledSources()
                                        val configs = manager.loadInstalledConfigs()
                                        sourceConfigs = configs.associate { it.sourceId to it.isEnabled }
                                        onSourcesChanged()
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.sources_uninstall),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.sources_uninstall))
                        }
                    }
                }
            }
        }
    }
}


