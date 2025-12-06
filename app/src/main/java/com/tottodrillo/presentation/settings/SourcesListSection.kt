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
import com.tottodrillo.domain.model.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.tottodrillo.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Update

/**
 * Sezione lista sorgenti nelle impostazioni
 */
@Composable
fun SourcesListSection(
    sourceManager: SourceManager,
    onSourcesChanged: () -> Unit = {},
    onUninstallSource: (String) -> Unit = {},
    onUpdateSource: () -> Unit = {},
    onInstallDefaultSources: () -> Unit = {},
    externalRefreshTrigger: Int = 0
) {
    val manager = sourceManager
    
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var sourceConfigs by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    
    // Ricarica le sorgenti quando cambia externalRefreshTrigger o quando viene montato il composable
    LaunchedEffect(externalRefreshTrigger) {
        sources = manager.getInstalledSources()
        // Carica lo stato abilitato/disabilitato
        val configs = manager.loadInstalledConfigs()
        sourceConfigs = configs.associate { it.sourceId to it.isEnabled }
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
        sources.forEach { source ->
            val isEnabled = sourceConfigs[source.id] ?: true
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (source.description != null) {
                                Text(
                                    text = source.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(R.string.sources_version_label, source.version),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    
                    // Pulsanti di azione
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Pulsante aggiorna
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


