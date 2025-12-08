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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tottodrillo.R
import com.tottodrillo.presentation.downloads.DownloadsViewModel
import com.tottodrillo.presentation.settings.SourceManagerEntryPoint
import com.tottodrillo.presentation.settings.SourceUpdateManagerEntryPoint
import com.tottodrillo.presentation.settings.RomRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.tottodrillo.domain.manager.SourceInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import dagger.hilt.android.components.ActivityComponent

/**
 * Componente per un gruppo di impostazioni espandibile
 */
@Composable
private fun ExpandableSettingsGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Header del gruppo (clickable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collassa" else "Espandi",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        // Contenuto del gruppo (mostrato solo se espanso)
        if (isExpanded) {
            Column(
                modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

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
    onInstallDefaultSources: () -> Unit = {},
    onSourcesChanged: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(),
    initialExpandedGroup: String? = null
) {
    val config by viewModel.downloadConfig.collectAsState()
    val showClearHistoryDialog by viewModel.showClearHistoryDialog.collectAsState()
    val context = LocalContext.current
    var igdbClientId by rememberSaveable { mutableStateOf(config.igdbClientId ?: "") }
    var igdbClientSecret by rememberSaveable { mutableStateOf(config.igdbClientSecret ?: "") }

    // Sincronizza i campi locale quando cambia la config (es. dopo riapertura schermata)
    LaunchedEffect(config.igdbClientId, config.igdbClientSecret) {
        igdbClientId = config.igdbClientId ?: ""
        igdbClientSecret = config.igdbClientSecret ?: ""
    }
    
    // Ottieni SourceManager e RomRepository tramite EntryPoint
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
    
    val romRepository = remember {
        try {
            val activity = context as? androidx.activity.ComponentActivity
            if (activity != null) {
                EntryPointAccessors.fromActivity(
                    activity,
                    RomRepositoryEntryPoint::class.java
                ).romRepository()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    val sourceUpdateManager = remember {
        try {
            val activity = context as? androidx.activity.ComponentActivity
            if (activity != null) {
                EntryPointAccessors.fromActivity(
                    activity,
                    SourceUpdateManagerEntryPoint::class.java
                ).sourceUpdateManager()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Scope per operazioni asincrone
    val settingsScope = remember {
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
    
    // Funzione per installare aggiornamento da URL
    val onUpdateSourceFromUrl: (String) -> Unit = { url ->
        // Ottieni le stringhe prima della coroutine (non possiamo usare stringResource in una coroutine)
        val progressTitle = context.getString(R.string.notif_source_update_download_progress)
        val updateAutoText = context.getString(R.string.sources_update_auto)
        val completedTitle = context.getString(R.string.notif_source_update_download_completed)
        val failedTitle = context.getString(R.string.notif_source_update_download_failed)
        
        settingsScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            val notificationId = 3000
            val channelId = "source_update_channel"
            
            try {
                // Crea il canale di notifica se non esiste
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
                    val channel = NotificationChannel(
                        channelId,
                        "Aggiornamenti Sorgenti",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notifiche per il download degli aggiornamenti delle sorgenti"
                    }
                    notificationManager.createNotificationChannel(channel)
                }
                
                // Mostra notifica iniziale immediatamente all'avvio del download
                if (notificationManager != null) {
                    val initialNotification = NotificationCompat.Builder(context, channelId)
                        .setContentTitle(progressTitle)
                        .setContentText(updateAutoText)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .build()
                    notificationManager.notify(notificationId, initialNotification)
                }
                
                // Ottieni OkHttpClient tramite EntryPoint
                val activity = context as? androidx.activity.ComponentActivity
                if (activity == null || sourceManager == null) {
                    if (notificationManager != null) {
                        val errorNotification = NotificationCompat.Builder(context, channelId)
                            .setContentTitle(failedTitle)
                            .setContentText("Errore: contesto non disponibile")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setAutoCancel(true)
                            .build()
                        notificationManager.notify(notificationId, errorNotification)
                    }
                    return@launch
                }
                
                // Crea un OkHttpClient semplice per il download
                val okHttpClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    android.util.Log.e("DownloadSettingsScreen", "Errore download sorgente: ${response.code}")
                    if (notificationManager != null) {
                        val errorNotification = NotificationCompat.Builder(context, channelId)
                            .setContentTitle(failedTitle)
                            .setContentText("Errore ${response.code}")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setAutoCancel(true)
                            .build()
                        notificationManager.notify(notificationId, errorNotification)
                    }
                    return@launch
                }
                
                // Salva in file temporaneo
                val tempFile = File(context.cacheDir, "source_update_${System.currentTimeMillis()}.zip")
                response.body?.byteStream()?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Installa la sorgente
                val installer = SourceInstaller(context, sourceManager!!)
                val result = installer.installFromZip(tempFile)
                
                result.fold(
                    onSuccess = {
                        if (notificationManager != null) {
                            val successNotification = NotificationCompat.Builder(context, channelId)
                                .setContentTitle(completedTitle)
                                .setContentText("${it.id} aggiornato con successo")
                                .setSmallIcon(R.drawable.ic_notification)
                                .setAutoCancel(true)
                                .build()
                            notificationManager.notify(notificationId, successNotification)
                        }
                        onSourcesChanged()
                    },
                    onFailure = { error ->
                        android.util.Log.e("DownloadSettingsScreen", "❌ Errore aggiornamento sorgente", error)
                        if (notificationManager != null) {
                            val errorNotification = NotificationCompat.Builder(context, channelId)
                                .setContentTitle(failedTitle)
                                .setContentText(error.message ?: "Errore sconosciuto")
                                .setSmallIcon(R.drawable.ic_notification)
                                .setAutoCancel(true)
                                .build()
                            notificationManager.notify(notificationId, errorNotification)
                        }
                    }
                )
                
                // Pulisci file temporaneo
                tempFile.delete()
            } catch (e: Exception) {
                android.util.Log.e("DownloadSettingsScreen", "Errore aggiornamento sorgente", e)
                if (notificationManager != null) {
                    val errorNotification = NotificationCompat.Builder(context, channelId)
                        .setContentTitle(failedTitle)
                        .setContentText(e.message ?: "Errore sconosciuto")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setAutoCancel(true)
                        .build()
                    notificationManager.notify(notificationId, errorNotification)
                }
            }
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

    // Stato per tracciare quali gruppi sono espansi
    var expandedGroups by rememberSaveable { 
        mutableStateOf(setOf<String>()) 
    }
    
    // Espandi automaticamente il gruppo iniziale se specificato
    LaunchedEffect(initialExpandedGroup) {
        if (initialExpandedGroup != null && !expandedGroups.contains(initialExpandedGroup)) {
            expandedGroups = expandedGroups + initialExpandedGroup
        }
    }
    
    fun toggleGroup(groupId: String) {
        expandedGroups = if (expandedGroups.contains(groupId)) {
            expandedGroups - groupId
        } else {
            expandedGroups + groupId
        }
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
            Spacer(modifier = Modifier.height(8.dp))

            // Variabile per permessi storage (usata nel gruppo Download)
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

            // Gruppo Download
            ExpandableSettingsGroup(
                title = stringResource(R.string.settings_download_path),
                icon = Icons.Default.Download,
                isExpanded = expandedGroups.contains("download"),
                onToggleExpand = { toggleGroup("download") }
            ) {
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

                // Permessi Storage (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Spacer(modifier = Modifier.height(12.dp))

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
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gruppo Rete
            ExpandableSettingsGroup(
                title = stringResource(R.string.settings_network),
                icon = Icons.Default.Wifi,
                isExpanded = expandedGroups.contains("network"),
                onToggleExpand = { toggleGroup("network") }
            ) {
                SettingItem(
                    title = stringResource(R.string.settings_wifi_only),
                    description = stringResource(R.string.settings_wifi_only_desc),
                    checked = config.useWifiOnly,
                    onCheckedChange = viewModel::updateWifiOnly
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gruppo Notifiche
            ExpandableSettingsGroup(
                title = stringResource(R.string.settings_notifications),
                icon = Icons.Default.Notifications,
                isExpanded = expandedGroups.contains("notifications"),
                onToggleExpand = { toggleGroup("notifications") }
            ) {
                SettingItem(
                    title = stringResource(R.string.settings_notifications),
                    description = stringResource(R.string.settings_notifications_desc),
                    checked = config.notificationsEnabled,
                    onCheckedChange = viewModel::updateNotifications
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gruppo Ricerca ROM
            ExpandableSettingsGroup(
                title = stringResource(R.string.settings_rom_info_search),
                icon = Icons.Default.Search,
                isExpanded = expandedGroups.contains("rom_search"),
                onToggleExpand = { toggleGroup("rom_search") }
            ) {
                RomInfoSearchProviderDropdown(
                    selectedProvider = config.romInfoSearchProvider,
                    onProviderSelected = viewModel::updateRomInfoSearchProvider
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.settings_igdb_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingItem(
                    title = stringResource(R.string.settings_igdb_enable),
                    description = stringResource(R.string.settings_igdb_enable_desc),
                    checked = config.igdbEnabled,
                    onCheckedChange = { enabled ->
                        igdbClientId = config.igdbClientId ?: ""
                        igdbClientSecret = config.igdbClientSecret ?: ""
                        viewModel.setIgdbEnabled(enabled)
                    }
                )

                if (config.igdbEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = igdbClientId,
                        onValueChange = {
                            igdbClientId = it
                            viewModel.setIgdbClientId(it)
                        },
                        label = { Text(stringResource(R.string.settings_igdb_client_id)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = igdbClientSecret,
                        onValueChange = {
                            igdbClientSecret = it
                            viewModel.setIgdbClientSecret(it)
                        },
                        label = { Text(stringResource(R.string.settings_igdb_client_secret)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_igdb_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api-docs.igdb.com/#getting-started"))
                            context.startActivity(intent)
                        },
                        textDecoration = TextDecoration.Underline
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gruppo Installazione
            ExpandableSettingsGroup(
                title = stringResource(R.string.settings_installation),
                icon = Icons.Default.Settings,
                isExpanded = expandedGroups.contains("installation"),
                onToggleExpand = { toggleGroup("installation") }
            ) {
                SettingItem(
                    title = stringResource(R.string.settings_delete_after_extraction),
                    description = stringResource(R.string.settings_delete_after_extraction_desc),
                    checked = config.deleteArchiveAfterExtraction,
                    onCheckedChange = viewModel::updateDeleteAfterExtract
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gruppo Storico
            ExpandableSettingsGroup(
                title = stringResource(R.string.settings_clear_history),
                icon = Icons.Default.History,
                isExpanded = expandedGroups.contains("history"),
                onToggleExpand = { toggleGroup("history") }
            ) {
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
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Gruppo Sorgenti
            ExpandableSettingsGroup(
                title = stringResource(R.string.settings_sources),
                icon = Icons.Default.Folder,
                isExpanded = expandedGroups.contains("sources"),
                onToggleExpand = { toggleGroup("sources") }
            ) {
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
                    
                    // Ricarica quando la schermata diventa visibile (es. quando si torna dalle impostazioni)
                    LaunchedEffect(Unit) {
                        refreshTrigger++
                    }
                    
                    // Ricarica anche quando viene chiamato onSourcesChanged (es. dopo l'installazione di una nuova sorgente)
                    LaunchedEffect(onSourcesChanged) {
                        // Incrementa il trigger per forzare il refresh quando cambiano le sorgenti
                        refreshTrigger++
                    }
                    
                    SourcesListSection(
                        sourceManager = it,
                        sourceUpdateManager = sourceUpdateManager,
                        externalRefreshTrigger = refreshTrigger,
                        onSourcesChanged = {
                            // Invalida la cache delle piattaforme e regioni
                            romRepository?.clearCache()
                            refreshTrigger++
                            // Notifica anche MainActivity che le sorgenti sono cambiate
                            onSourcesChanged()
                        },
                        onUninstallSource = { sourceId ->
                            // La disinstallazione è già gestita in SourcesListSection
                            // Invalida la cache delle piattaforme e regioni
                            romRepository?.clearCache()
                            refreshTrigger++
                            onSourcesChanged()
                        },
                        onUpdateSource = {
                            // Apri il file picker per selezionare il nuovo ZIP
                            onInstallSource()
                        },
                        onUpdateSourceFromUrl = onUpdateSourceFromUrl,
                        onInstallDefaultSources = {
                            onInstallDefaultSources()
                            // Ricarica dopo l'installazione
                            refreshTrigger++
                            onSourcesChanged()
                        }
                    )
                } ?: run {
                    Text(
                        text = stringResource(R.string.sources_list_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Informazioni app (sempre visibili)
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
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RomInfoSearchProviderDropdown(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    val providers = listOf("gamefaqs", "mobygames")
    val providerLabels = mapOf(
        "gamefaqs" to stringResource(R.string.settings_rom_info_search_gamefaqs),
        "mobygames" to stringResource(R.string.settings_rom_info_search_mobygames)
    )
    
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = providerLabels[selectedProvider] ?: selectedProvider,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_rom_info_search)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(providerLabels[provider] ?: provider) },
                    onClick = {
                        onProviderSelected(provider)
                        expanded = false
                    }
                )
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
