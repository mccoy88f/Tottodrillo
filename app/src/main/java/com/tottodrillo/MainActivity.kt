package com.tottodrillo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.tottodrillo.presentation.components.StoragePermissionDialog
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.tottodrillo.R
import com.tottodrillo.data.repository.DownloadConfigRepository
import com.tottodrillo.domain.manager.PlatformManager
import com.tottodrillo.domain.model.SourcesVersionsResponse
import com.tottodrillo.domain.repository.RomRepository
import com.tottodrillo.presentation.downloads.DownloadsViewModel
import com.tottodrillo.presentation.navigation.TottodrilloNavGraph
import com.tottodrillo.presentation.theme.TottodrilloTheme
import com.tottodrillo.util.StoragePermissionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

/**
 * MainActivity principale
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var romRepository: RomRepository
    
    @Inject
    lateinit var platformManager: PlatformManager
    
    @Inject
    lateinit var configRepository: DownloadConfigRepository
    
    @Inject
    lateinit var sourceManager: com.tottodrillo.domain.manager.SourceManager
    
    @Inject
    lateinit var okHttpClient: OkHttpClient
    
    @Inject
    lateinit var updateManager: com.tottodrillo.domain.manager.UpdateManager
    
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    companion object {
        private const val UPDATE_NOTIFICATION_CHANNEL_ID = "update_channel"
        private const val UPDATE_NOTIFICATION_ID = 2000
    }

    private val requestNotificationPermission = 
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val downloadsViewModel: DownloadsViewModel by viewModels()
    
    // Slug della ROM da aprire quando l'app viene avviata da una notifica
    private var pendingRomSlug: String? = null

    private val openDownloadFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                val path = convertTreeUriToPath(it)
                if (path != null) {
                    downloadsViewModel.updateDownloadPath(path)
                }
            }
        }

    private val openEsDeFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                val path = convertTreeUriToPath(it)
                if (path != null) {
                    downloadsViewModel.updateEsDeRomsPath(path)
                }
            }
        }

    private var pendingExtraction: Triple<String, String, String>? = null // archivePath, romTitle, romSlug

    // Callback per notificare quando una sorgente viene installata
    private var onSourceInstalled: (() -> Unit)? = null

    private val installSourceLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                activityScope.launch {
                    try {
                        // Copia il file nella cache temporanea
                        val tempFile = File(this@MainActivity.cacheDir, "source_install_${System.currentTimeMillis()}.zip")
                        this@MainActivity.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Installa la sorgente
                        val installer = com.tottodrillo.domain.manager.SourceInstaller(
                            this@MainActivity,
                            sourceManager
                        )
                        val result = installer.installFromZip(tempFile)
                        result.fold(
                            onSuccess = { metadata ->
                                // Notifica che una sorgente è stata installata
                                onSourceInstalled?.invoke()
                            },
                            onFailure = { error ->
                                android.util.Log.e("MainActivity", "❌ Errore installazione sorgente", error)
                            }
                        )
                        
                        // Pulisci file temporaneo
                        tempFile.delete()
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Errore nell'installazione sorgente", e)
                    }
                }
            }
        }
    
    private val openExtractionFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val (archivePath, romTitle, romSlug) = pendingExtraction ?: return@registerForActivityResult
            uri?.let {
                val path = convertTreeUriToPath(it)
                if (path != null) {
                    downloadsViewModel.startExtraction(archivePath, path, romTitle, romSlug)
                } else {
                    android.util.Log.e("MainActivity", "❌ Impossibile convertire URI in path: $uri")
                    // TODO: Potremmo dover usare DocumentFile invece di File per SD card
                }
            } ?: run {
                android.util.Log.e("MainActivity", "❌ URI null per estrazione")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Gestisci Intent da notifiche
        handleNotificationIntent(intent)
        
        // Salva l'Intent per gestirlo anche quando NavGraph è pronto
        setIntent(intent)

        // Richiedi permesso notifiche su Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PermissionChecker.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        setContent {
            TottodrilloTheme {
                FirstLaunchHandler(
                    configRepository = configRepository,
                    onRequestStoragePermission = {
                        StoragePermissionManager.requestManageExternalStoragePermission(this@MainActivity)
                    },
                    onPermissionDialogDismissed = {
                        activityScope.launch {
                            configRepository.setFirstLaunchCompleted()
                        }
                    }
                )
                
                // Stato per il dialog di aggiornamento
                var updateRelease by remember { mutableStateOf<com.tottodrillo.domain.manager.GitHubRelease?>(null) }
                
                // Verifica aggiornamenti all'avvio (solo una volta)
                LaunchedEffect(Unit) {
                    activityScope.launch {
                        try {
                            val release = updateManager.checkForUpdate()
                            if (release != null) {
                                updateRelease = release
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Errore verifica aggiornamento", e)
                        }
                    }
                }
                
                // Dialog aggiornamento
                updateRelease?.let { release ->
                    com.tottodrillo.presentation.components.UpdateDialog(
                        release = release,
                        onDismiss = { updateRelease = null },
                        onDownload = {
                            updateRelease = null
                            activityScope.launch {
                                downloadAndInstallUpdate(release)
                            }
                        }
                    )
                }
                
                // Controlla se ci sono sorgenti abilitate (non solo installate)
                var hasEnabledSources by remember { mutableStateOf<Boolean?>(null) }
                var refreshTrigger by remember { mutableStateOf(0) }
                var homeRefreshTrigger by remember { mutableStateOf(0) }
                
                // Imposta il callback per aggiornare lo stato quando viene installata una sorgente
                LaunchedEffect(Unit) {
                    onSourceInstalled = {
                        refreshTrigger++
                    }
                }
                
                // Ricarica lo stato delle sorgenti quando cambia refreshTrigger o all'avvio
                LaunchedEffect(refreshTrigger) {
                    // Delay per assicurarsi che le modifiche siano state salvate su disco
                    if (refreshTrigger > 0) {
                        kotlinx.coroutines.delay(500) // Delay più lungo per assicurarsi che il salvataggio sia completato
                    }
                    val hasInstalled = sourceManager.hasInstalledSources()
                    val hasEnabled = if (hasInstalled) {
                        sourceManager.hasEnabledSources()
                    } else {
                        false
                    }
                    val previousState = hasEnabledSources
                    hasEnabledSources = hasEnabled
                    // Se lo stato è cambiato (qualsiasi cambiamento), forza sempre il refresh della home
                    if (previousState != null && previousState != hasEnabled) {
                        homeRefreshTrigger++
                    } else if (refreshTrigger > 0) {
                        // Anche se lo stato non è cambiato, se c'è stato un trigger, forza comunque il refresh
                        // (per assicurarsi che la home si aggiorni dopo attivazione/disattivazione)
                        homeRefreshTrigger++
                    }
                }
                
                // Controlla se ci sono sorgenti installate
                var hasInstalledSources by remember { mutableStateOf<Boolean?>(null) }
                
                LaunchedEffect(refreshTrigger) {
                    hasInstalledSources = sourceManager.hasInstalledSources()
                }
                
                when {
                    // Nessuna sorgente installata
                    hasInstalledSources == false -> {
                        com.tottodrillo.presentation.sources.NoSourcesScreen(
                            onInstallSource = {
                                installSourceLauncher.launch("application/zip")
                            },
                            onInstallDefaultSources = {
                                activityScope.launch {
                                    try {
                                        installDefaultSources()
                                        refreshTrigger++
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "❌ Errore installazione sorgenti predefinite", e)
                                    }
                                }
                            }
                        )
                    }
                    hasEnabledSources == false -> {
                        // Stato per mostrare le impostazioni quando necessario
                        var showSettings by remember { mutableStateOf(false) }
                        var showSourcesSection by remember { mutableStateOf(false) }
                        
                        if (showSettings) {
                            // Mostra direttamente la schermata delle impostazioni
                            com.tottodrillo.presentation.settings.DownloadSettingsScreen(
                                onNavigateBack = { 
                                    showSettings = false
                                    showSourcesSection = false
                                },
                                initialExpandedGroup = if (showSourcesSection) "sources" else null,
                                onSelectFolder = { openDownloadFolderLauncher.launch(null) },
                                onSelectEsDeFolder = { openEsDeFolderLauncher.launch(null) },
                                onRequestStoragePermission = {
                                    StoragePermissionManager.requestManageExternalStoragePermission(this@MainActivity)
                                },
                                onInstallSource = {
                                    installSourceLauncher.launch("application/zip")
                                },
                                onInstallDefaultSources = {
                                    activityScope.launch {
                                        try {
                                            installDefaultSources()
                                            refreshTrigger++
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "❌ Errore installazione sorgenti predefinite", e)
                                        }
                                    }
                                },
                                onSourcesChanged = {
                                    // Quando cambiano le sorgenti, ricontrolla lo stato
                                    refreshTrigger++
                                }
                            )
                        } else {
                            // Mostra schermata "Nessuna sorgente abilitata"
                            com.tottodrillo.presentation.sources.NoEnabledSourcesScreen(
                                onNavigateToSettings = {
                                    showSourcesSection = true
                                    showSettings = true
                                },
                                onInstallDefaultSources = {
                                    activityScope.launch {
                                        try {
                                            installDefaultSources()
                                            refreshTrigger++
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "❌ Errore installazione sorgenti predefinite", e)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    hasEnabledSources == null -> {
                        // Loading - mostra schermata normale (verrà aggiornata)
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            // Mostra loading o app normale
                        }
                    }
                    hasEnabledSources == true -> {
                        // Mostra app normale
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            TottodrilloNavGraph(
                        initialRomSlug = pendingRomSlug,
                        onOpenDownloadFolderPicker = {
                            openDownloadFolderLauncher.launch(null)
                        },
                        onOpenEsDeFolderPicker = {
                            openEsDeFolderLauncher.launch(null)
                        },
                        onRequestStoragePermission = {
                            StoragePermissionManager.requestManageExternalStoragePermission(this@MainActivity)
                        },
                        onInstallSource = {
                            installSourceLauncher.launch("application/zip")
                        },
                        onSourcesStateChanged = {
                            // Incrementa il trigger per ricontrollare lo stato delle sorgenti
                            refreshTrigger++
                        },
                        onHomeRefresh = {
                            // Incrementa il trigger per forzare il refresh della home
                            homeRefreshTrigger++
                        },
                        homeRefreshKey = homeRefreshTrigger,
                        onRequestExtraction = { archivePath, romTitle, romSlug, platformCode ->
                            // Controlla se ES-DE è abilitato
                            activityScope.launch {
                                try {
                                    val config = configRepository.downloadConfig.first()
                                    
                                    if (config.enableEsDeCompatibility && !config.esDeRomsPath.isNullOrBlank()) {
                                        // Usa automaticamente la cartella ES-DE
                                        // Cerca il motherCode in tutte le sorgenti disponibili
                                        var motherCode: String? = null
                                        val availableSources = platformManager.getAvailableSources()
                                        
                                        // Prova prima a cercare in tutte le sorgenti
                                        for (sourceName in availableSources) {
                                            val found = platformManager.getMotherCodeFromSourceCode(platformCode, sourceName)
                                            if (found != null) {
                                                motherCode = found
                                                break
                                            }
                                        }
                                        
                                        // Se non trovato, usa il platformCode direttamente come motherCode
                                        // (ES-DE usa i motherCode come nomi delle cartelle, quindi potrebbe già essere corretto)
                                        if (motherCode == null) {
                                            motherCode = platformCode
                                        }
                                        
                                        // Verifica che il path ES-DE sia valido
                                        val esDeBasePath = config.esDeRomsPath
                                        if (esDeBasePath != null && configRepository.isPathValid(esDeBasePath)) {
                                            val esDePath = "$esDeBasePath/$motherCode"
                                            downloadsViewModel.startExtraction(archivePath, esDePath, romTitle, romSlug)
                                        } else {
                                            android.util.Log.w("MainActivity", "⚠️ Path ES-DE non valido: $esDeBasePath, uso picker manuale")
                                            pendingExtraction = Triple(archivePath, romTitle, romSlug)
                                            openExtractionFolderLauncher.launch(null)
                                        }
                                    } else {
                                        // Usa il picker manuale
                                        pendingExtraction = Triple(archivePath, romTitle, romSlug)
                                        openExtractionFolderLauncher.launch(null)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "❌ Errore nel controllo ES-DE", e)
                                    // In caso di errore, usa il picker manuale
                                    pendingExtraction = Triple(archivePath, romTitle, romSlug)
                                    openExtractionFolderLauncher.launch(null)
                                }
                            }
                        }
                    )
                        }
                    }
                    else -> {
                        // Loading
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            // Mostra loading mentre verifica sorgenti
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Gestisce Intent da notifiche per aprire la ROM
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action == "OPEN_ROM_DETAIL") {
            val romSlug = intent.getStringExtra("romSlug")
            if (romSlug != null) {
                android.util.Log.d("MainActivity", "📱 Intent ricevuto da notifica: romSlug=$romSlug")
                pendingRomSlug = romSlug
            }
        }
    }

    /**
     * Converte una tree URI in un percorso filesystem
     * Supporta sia lo storage "primary" che le SD card esterne
     */
    private fun convertTreeUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            if (parts.isEmpty()) {
                android.util.Log.e("MainActivity", "docId vuoto per URI: $uri")
                return null
            }

            val type = parts[0]
            val relPath = if (parts.size > 1) parts[1] else ""

            android.util.Log.d("MainActivity", "convertTreeUriToPath: type=$type, relPath=$relPath, docId=$docId")

            if (type.equals("primary", ignoreCase = true)) {
                // Storage principale (memoria interna)
                val base = Environment.getExternalStorageDirectory().path
                val path = if (relPath.isNotEmpty()) "$base/$relPath" else base
                path
            } else {
                // SD card esterna o altro storage
                // Prova a ottenere il percorso usando StorageVolume
                val path = getExternalStoragePath(uri, type, relPath)
                if (path != null) {
                    path
                } else {
                    android.util.Log.e("MainActivity", "❌ Impossibile ottenere path per type=$type")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore nella conversione URI to Path", e)
            null
        }
    }
    
    /**
     * Ottiene il percorso per storage esterni (SD card)
     */
    private fun getExternalStoragePath(uri: Uri, storageId: String, relPath: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: usa StorageVolume
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                val storageVolumes = storageManager.storageVolumes
                
                for (volume in storageVolumes) {
                    val volumeUuid = volume.uuid
                    val volumePath = volume.directory?.path
                    
                    android.util.Log.d("MainActivity", "  - Volume: uuid=$volumeUuid, path=$volumePath, isRemovable=${volume.isRemovable}, isPrimary=${volume.isPrimary}")
                    
                    // Controlla se questo volume corrisponde allo storageId
                    // Il docId per SD card può essere l'UUID o un ID simile
                    if (volumeUuid != null && (volumeUuid == storageId || storageId.contains(volumeUuid) || volumeUuid.contains(storageId))) {
                        if (volumePath != null) {
                            val path = if (relPath.isNotEmpty()) "$volumePath/$relPath" else volumePath
                            return path
                        }
                    }
                }
                
                // Fallback 1: Prova il formato standard /storage/[storageId]
                val standardPath = "/storage/$storageId"
                val standardFile = java.io.File(standardPath)
                if (standardFile.exists() && standardFile.canRead()) {
                    val path = if (relPath.isNotEmpty()) "$standardPath/$relPath" else standardPath
                    return path
                }
                
                // Fallback 2: Prova /mnt/media_rw/[storageId] (alcuni dispositivi)
                val mediaRwPath = "/mnt/media_rw/$storageId"
                val mediaRwFile = java.io.File(mediaRwPath)
                if (mediaRwFile.exists() && mediaRwFile.canRead()) {
                    val path = if (relPath.isNotEmpty()) "$mediaRwPath/$relPath" else mediaRwPath
                    return path
                }
                
                // Fallback 3: Prova a ottenere il path dal URI usando MediaStore
                val path = getPathFromUri(uri)
                if (path != null) {
                    return path
                }
            } else {
                // Android < 10: prova metodi alternativi
                // Prova il formato standard
                val standardPath = "/storage/$storageId"
                val standardFile = java.io.File(standardPath)
                if (standardFile.exists()) {
                    val path = if (relPath.isNotEmpty()) "$standardPath/$relPath" else standardPath
                    return path
                }
                
                val path = getPathFromUri(uri)
                if (path != null) {
                    return path
                }
            }
            
            android.util.Log.e("MainActivity", "❌ Nessun path trovato per storageId=$storageId")
            null
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore nel recupero path storage esterno", e)
            null
        }
    }
    
    /**
     * Prova a ottenere il percorso file da un URI usando vari metodi
     */
    /**
     * Scarica e installa le sorgenti predefinite
     * Legge le informazioni dal file sources-versions.json nel repository Tottodrillo-Source
     */
    private suspend fun installDefaultSources() = withContext(Dispatchers.IO) {
        val sourcesVersionsUrl = "https://raw.githubusercontent.com/mccoy88f/Tottodrillo-Source/refs/heads/main/sources-versions.json"
        
        try {
            // Scarica il file sources-versions.json
            val listRequest = Request.Builder()
                .url(sourcesVersionsUrl)
                .header("Accept", "application/json")
                .build()
            
            val listResponse = okHttpClient.newCall(listRequest).execute()
            if (!listResponse.isSuccessful) {
                android.util.Log.e("MainActivity", "❌ Errore download sources-versions.json: ${listResponse.code}")
                return@withContext
            }
            
            // Leggi il JSON
            val responseBody = listResponse.body?.string()
            if (responseBody == null) {
                android.util.Log.e("MainActivity", "❌ Response body null")
                return@withContext
            }
            
            // Parse del JSON
            val gson = com.google.gson.Gson()
            val versionsResponse = gson.fromJson(
                responseBody,
                SourcesVersionsResponse::class.java
            )
            
            val sources = versionsResponse.sources
            if (sources.isEmpty()) {
                android.util.Log.w("MainActivity", "⚠️ Nessuna sorgente trovata in sources-versions.json")
                return@withContext
            }
            
            android.util.Log.d("MainActivity", "📋 Trovate ${sources.size} sorgenti in sources-versions.json")
            
            val installer = com.tottodrillo.domain.manager.SourceInstaller(
                this@MainActivity,
                sourceManager
            )
            
            // Verifica versione app corrente
            val currentAppVersion = try {
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.GET_META_DATA
                )
                packageInfo.versionName ?: "0.0.0"
            } catch (e: Exception) {
                "0.0.0"
            }
            
            // Scarica e installa ogni sorgente
            for (sourceInfo in sources) {
                // Verifica compatibilità con versione app
                if (sourceInfo.minAppVersion != null) {
                    if (!isVersionNewerOrEqual(currentAppVersion, sourceInfo.minAppVersion)) {
                        android.util.Log.w(
                            "MainActivity",
                            "⚠️ Sorgente ${sourceInfo.id} richiede app versione ${sourceInfo.minAppVersion}, attuale: $currentAppVersion"
                        )
                        continue
                    }
                }
                
                val url = sourceInfo.downloadUrl
                try {
                    
                    // Scarica il file ZIP
                    val request = Request.Builder()
                        .url(url)
                        .build()
                    
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        android.util.Log.e("MainActivity", "❌ Errore download sorgente da $url: ${response.code}")
                        continue
                    }
                    
                    // Usa l'ID della sorgente dal JSON
                    val sourceId = sourceInfo.id
                    
                    // Salva in un file temporaneo
                    val tempFile = File(this@MainActivity.cacheDir, "source_${sourceId}_${System.currentTimeMillis()}.zip")
                    response.body?.byteStream()?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Installa la sorgente
                    val result = installer.installFromZip(tempFile)
                    result.fold(
                        onSuccess = { metadata ->
                            // Abilita la sorgente di default
                            sourceManager.setSourceEnabled(metadata.id, true)
                        },
                        onFailure = { error ->
                            android.util.Log.e("MainActivity", "❌ Errore installazione sorgente $sourceId", error)
                        }
                    )
                    
                    // Pulisci file temporaneo
                    tempFile.delete()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "❌ Errore durante installazione sorgente da $url", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Errore durante download sources-versions.json", e)
        }
    }
    
    /**
     * Verifica se una versione è più recente o uguale a un'altra
     */
    private fun isVersionNewerOrEqual(version1: String, version2: String): Boolean {
        if (version1 == version2) return true
        
        val parts1 = version1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = version2.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrElse(i) { 0 }
            val part2 = parts2.getOrElse(i) { 0 }
            
            when {
                part1 > part2 -> return true
                part1 < part2 -> return false
            }
        }
        
        return false
    }
    
    /**
     * Crea il canale di notifica per gli aggiornamenti
     */
    private fun createUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_NOTIFICATION_CHANNEL_ID,
                "Aggiornamenti",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche per il download degli aggiornamenti dell'app"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Mostra una notifica di progresso per il download dell'aggiornamento
     */
    private fun showUpdateDownloadProgress(progress: Int, max: Int, versionName: String) {
        val progressPercent = if (max > 0) {
            (progress * 100 / max).coerceIn(0, 100)
        } else {
            0
        }
        
        val notification = NotificationCompat.Builder(this, UPDATE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_update_download_progress))
            .setContentText("$versionName - $progressPercent%")
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(max, progress, max == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        
        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }
    
    /**
     * Mostra una notifica di completamento per il download dell'aggiornamento
     */
    private fun showUpdateDownloadCompleted(versionName: String) {
        val notification = NotificationCompat.Builder(this, UPDATE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_update_download_completed))
            .setContentText(versionName)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }
    
    /**
     * Mostra una notifica di errore per il download dell'aggiornamento
     */
    private fun showUpdateDownloadError(versionName: String, error: String) {
        val notification = NotificationCompat.Builder(this, UPDATE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_update_download_failed))
            .setContentText("$versionName: $error")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }
    
    /**
     * Scarica e installa l'APK di aggiornamento
     */
    private suspend fun downloadAndInstallUpdate(release: com.tottodrillo.domain.manager.GitHubRelease) {
        // Crea il canale di notifica PRIMA di entrare nel blocco IO
        createUpdateNotificationChannel()
        
        // Mostra notifica iniziale IMMEDIATAMENTE quando parte il download
        showUpdateDownloadProgress(0, 0, release.name)
        
        withContext(Dispatchers.IO) {
            try {
                
                val apkUrl = updateManager.getApkDownloadUrl(release)
                if (apkUrl == null) {
                    android.util.Log.e("MainActivity", "❌ Nessun APK trovato nella release")
                    withContext(Dispatchers.Main) {
                        showUpdateDownloadError(release.name, "APK non trovato")
                    }
                    return@withContext
                }
                
                // Scarica l'APK
                val request = Request.Builder()
                    .url(apkUrl)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    android.util.Log.e("MainActivity", "❌ Errore download APK: ${response.code}")
                    withContext(Dispatchers.Main) {
                        showUpdateDownloadError(release.name, "Errore ${response.code}")
                    }
                    return@withContext
                }
                
                // Ottieni la dimensione totale del file (se disponibile)
                val contentLength = response.body?.contentLength() ?: -1L
                val totalBytes = if (contentLength > 0) contentLength else 0L
                
                // Aggiorna la notifica con la dimensione totale se disponibile
                if (totalBytes > 0) {
                    withContext(Dispatchers.Main) {
                        showUpdateDownloadProgress(0, totalBytes.toInt(), release.name)
                    }
                }
                
                // Salva l'APK in cache con progresso
                val apkFile = File(cacheDir, "update_${release.tagName}.apk")
                var downloadedBytes = 0L
                var lastNotifiedProgress = -1
                
                response.body?.byteStream()?.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            // Aggiorna la notifica ogni 1% o ogni 100KB
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                
                                // Notifica solo se la percentuale è cambiata o ogni 100KB
                                if (progress != lastNotifiedProgress || downloadedBytes % 100_000L == 0L) {
                                    lastNotifiedProgress = progress
                                    withContext(Dispatchers.Main) {
                                        showUpdateDownloadProgress(
                                            downloadedBytes.toInt(),
                                            totalBytes.toInt(),
                                            release.name
                                        )
                    }
                                }
                            } else {
                                // Se non conosciamo la dimensione totale, aggiorna ogni 100KB
                                if (downloadedBytes % 100_000L == 0L) {
                                    withContext(Dispatchers.Main) {
                                        showUpdateDownloadProgress(0, 0, release.name)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Mostra notifica di completamento
                withContext(Dispatchers.Main) {
                    showUpdateDownloadCompleted(release.name)
                }
                
                // Installa l'APK
                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Errore download/installazione APK", e)
                withContext(Dispatchers.Main) {
                    showUpdateDownloadError(release.name, e.message ?: "Errore sconosciuto")
                }
            }
        }
    }
    
    /**
     * Installa un file APK
     */
    private fun installApk(apkFile: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Errore installazione APK", e)
        }
    }
    
    private fun getPathFromUri(uri: Uri): String? {
        return try {
            // Metodo 1: Prova con DocumentsContract
            if (DocumentsContract.isDocumentUri(this, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val path = parts[1]
                    
                    if (type == "primary") {
                        return Environment.getExternalStorageDirectory().path + "/" + path
                    } else {
                        // Per SD card, prova a cercare nei volumi
                        val externalStorage = "/storage/$type"
                        val fullPath = if (path.startsWith("/")) "$externalStorage$path" else "$externalStorage/$path"
                        val file = java.io.File(fullPath)
                        if (file.exists()) {
                            return fullPath
                        }
                    }
                }
            }
            
            // Metodo 2: Prova con MediaStore (per Android < 10)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        val path = it.getString(columnIndex)
                        if (path != null) {
                            return java.io.File(path).parent
                        }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Errore in getPathFromUri", e)
            null
        }
    }
}

/**
 * Composable per gestire il primo avvio e mostrare il dialog informativo sui permessi
 */
@Composable
fun FirstLaunchHandler(
    configRepository: DownloadConfigRepository,
    onRequestStoragePermission: () -> Unit,
    onPermissionDialogDismissed: () -> Unit
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isFirstLaunchChecked by remember { mutableStateOf(false) }

    // Controlla se è il primo avvio solo su Android 11+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isFirstLaunchCompleted = configRepository.isFirstLaunchCompleted()
            if (!isFirstLaunchCompleted) {
                val hasPermission = StoragePermissionManager.hasManageExternalStoragePermission(context)
                if (!hasPermission) {
                    showPermissionDialog = true
                } else {
                    // Se ha già il permesso, segna come completato
                    configRepository.setFirstLaunchCompleted()
                }
            }
            isFirstLaunchChecked = true
        } else {
            isFirstLaunchChecked = true
        }
    }

    if (showPermissionDialog && isFirstLaunchChecked) {
        StoragePermissionDialog(
            onDismiss = {
                showPermissionDialog = false
                onPermissionDialogDismissed()
            },
            onConfirm = {
                showPermissionDialog = false
                onRequestStoragePermission()
                onPermissionDialogDismissed()
            }
        )
    }
}
