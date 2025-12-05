package com.tottodrillo.presentation.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import com.tottodrillo.util.StoragePermissionManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.tottodrillo.R
import com.tottodrillo.presentation.downloads.DownloadsViewModel
import com.tottodrillo.presentation.settings.SourceManagerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent

/**
 * Schermata impostazioni download
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    onNavigateBack: () -> Unit,
    onSelectFolder: () -> Unit,
    onSelectEsDeFolder: () -> Unit = {},
    onRequestStoragePermission: () -> Unit = {},
    onInstallSource: () -> Unit = {},
    onSourcesChanged: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val config by viewModel.downloadConfig.collectAsState()
    val showClearHistoryDialog by viewModel.showClearHistoryDialog.collectAsState()
    val context = LocalContext.current
    
    // Ottieni SourceManager tramite EntryPoint
    val sourceManager = remember {
        try {
            val activity = context as? androidx.activity.ComponentActivity
            if (activity != null) {
                EntryPointAccessors.fromActivity(
                    activity,
                    SourceManagerEntryPoint::class.java
                ).sourceManager()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Dialog di conferma per cancellare storico
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearHistoryDialog() },
            title = { Text(stringResource(R.string.settings_clear_history_dialog_title)) },
            text = { 
                Text(stringResource(R.string.settings_clear_history_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearDownloadHistory() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.settings_clear_history_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearHistoryDialog() }) {
                    Text(stringResource(R.string.settings_clear_history_dialog_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Path di download section
            Text(
                text = stringResource(R.string.settings_download_path),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectFolder),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.padding(horizontal = 12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_download_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = config.downloadPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_available_space, formatBytes(viewModel.getAvailableSpace(config.downloadPath))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permessi Storage (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Text(
                    text = stringResource(R.string.settings_storage_permission),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                var hasPermission by remember { 
                    mutableStateOf(StoragePermissionManager.hasManageExternalStoragePermission(context))
                }

                // Aggiorna lo stato quando l'Activity torna in primo piano (es. dopo aver concesso il permesso)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            // Aggiorna lo stato del permesso quando l'Activity torna in primo piano
                            hasPermission = StoragePermissionManager.hasManageExternalStoragePermission(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasPermission) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )

                        Spacer(modifier = Modifier.padding(horizontal = 12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_storage_permission),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hasPermission) {
                                    stringResource(R.string.settings_storage_permission_granted)
                                } else {
                                    stringResource(R.string.settings_storage_permission_denied)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!hasPermission) {
                            TextButton(
                                onClick = {
                                    onRequestStoragePermission()
                                    // Lo stato verrà aggiornato automaticamente dal LifecycleObserver quando l'utente torna
                                }
                            ) {
                                Text(stringResource(R.string.settings_storage_permission_request))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.settings_storage_permission_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Opzioni rete
            Text(
                text = stringResource(R.string.settings_network),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = stringResource(R.string.settings_wifi_only),
                description = stringResource(R.string.settings_wifi_only_desc),
                checked = config.useWifiOnly,
                onCheckedChange = viewModel::updateWifiOnly
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Notifiche
            Text(
                text = stringResource(R.string.settings_notifications),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = stringResource(R.string.settings_notifications),
                description = stringResource(R.string.settings_notifications_desc),
                checked = config.notificationsEnabled,
                onCheckedChange = viewModel::updateNotifications
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Opzioni estrazione
            Text(
                text = stringResource(R.string.settings_installation),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = stringResource(R.string.settings_delete_after_extraction),
                description = stringResource(R.string.settings_delete_after_extraction_desc),
                checked = config.deleteArchiveAfterExtraction,
                onCheckedChange = viewModel::updateDeleteAfterExtract
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Compatibilità ES-DE
            Text(
                text = stringResource(R.string.settings_esde_compatibility),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = stringResource(R.string.settings_esde_compatibility),
                description = stringResource(R.string.settings_esde_compatibility_desc),
                checked = config.enableEsDeCompatibility,
                onCheckedChange = viewModel::updateEsDeCompatibility
            )

            if (config.enableEsDeCompatibility) {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSelectEsDeFolder),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.padding(horizontal = 12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_esde_folder),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = config.esDeRomsPath ?: stringResource(R.string.settings_esde_folder_not_selected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gestione storico
            Text(
                text = stringResource(R.string.settings_clear_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { viewModel.showClearHistoryConfirmation() }),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_history),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_clear_history_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sorgenti
            Text(
                text = stringResource(R.string.settings_sources),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Pulsante per installare nuove sorgenti
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onInstallSource),
                shape = RoundedCornerShape(12.dp),
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
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.sources_install_new),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Lista sorgenti installate
            sourceManager?.let {
                var refreshTrigger by remember { mutableStateOf(0) }
                
                SourcesListSection(
                    sourceManager = it,
                    onSourcesChanged = {
                        // Notifica che le sorgenti sono cambiate
                        android.util.Log.d("DownloadSettingsScreen", "🔄 onSourcesChanged ricevuto da SourcesListSection")
                        refreshTrigger++
                        // Notifica anche MainActivity che le sorgenti sono cambiate
                        android.util.Log.d("DownloadSettingsScreen", "🔄 Chiamo onSourcesChanged() callback")
                        onSourcesChanged()
                        android.util.Log.d("DownloadSettingsScreen", "🔄 onSourcesChanged() callback chiamato")
                    }
                )
                
                // Ricarica la lista quando cambiano le sorgenti
                LaunchedEffect(refreshTrigger) {
                    if (refreshTrigger > 0) {
                        // Le sorgenti sono cambiate, ricarica la lista
                        kotlinx.coroutines.delay(100) // Piccolo delay per assicurarsi che il cambio sia stato salvato
                    }
                }
            } ?: run {
                Text(
                    text = stringResource(R.string.sources_list_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Informazioni app
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tottodrillo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Versione app - calcolata prima della composizione
                val versionName = remember {
                    try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        packageInfo.versionName ?: "1.0"
                    } catch (e: PackageManager.NameNotFoundException) {
                        "1.0"
                    }
                }
                
                Text(
                    text = stringResource(R.string.settings_version_label, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.settings_author_label, "McCoy88f"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mccoy88f/Tottodrillo"))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(
                        text = stringResource(R.string.settings_github),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Sezione Supporto
                Text(
                    text = stringResource(R.string.settings_support_me),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.settings_support_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Link Buy Me a Coffee
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/mccoy88f"))
                            context.startActivity(intent)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(
                        text = stringResource(R.string.settings_buy_me_coffee),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Link PayPal
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/mccoy88f?country.x=IT&locale.x=it_IT"))
                            context.startActivity(intent)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(
                        text = stringResource(R.string.settings_paypal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
