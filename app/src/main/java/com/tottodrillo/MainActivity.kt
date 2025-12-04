package com.tottodrillo

import android.Manifest
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
import androidx.compose.ui.platform.LocalContext
import com.tottodrillo.presentation.components.StoragePermissionDialog
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.tottodrillo.data.repository.DownloadConfigRepository
import com.tottodrillo.domain.manager.PlatformManager
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
    
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    private val openExtractionFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val (archivePath, romTitle, romSlug) = pendingExtraction ?: return@registerForActivityResult
            uri?.let {
                android.util.Log.d("MainActivity", "📁 URI selezionato per estrazione: $uri")
                val path = convertTreeUriToPath(it)
                if (path != null) {
                    android.util.Log.d("MainActivity", "✅ Path convertito: $path")
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
                        onRequestExtraction = { archivePath, romTitle, romSlug, platformCode ->
                            // Controlla se ES-DE è abilitato
                            activityScope.launch {
                                try {
                                    val config = configRepository.downloadConfig.first()
                                    android.util.Log.d("MainActivity", "🔍 Config ES-DE: enabled=${config.enableEsDeCompatibility}, path=${config.esDeRomsPath}")
                                    
                                    if (config.enableEsDeCompatibility && !config.esDeRomsPath.isNullOrBlank()) {
                                        // Usa automaticamente la cartella ES-DE
                                        val motherCode = platformManager.getMotherCodeFromCrocDbCode(platformCode)
                                        android.util.Log.d("MainActivity", "🔍 Platform code: $platformCode -> Mother code: $motherCode")
                                        
                                        if (motherCode != null) {
                                            val esDePath = "${config.esDeRomsPath}/$motherCode"
                                            android.util.Log.d("MainActivity", "✅ ES-DE abilitato: installazione in $esDePath")
                                            downloadsViewModel.startExtraction(archivePath, esDePath, romTitle, romSlug)
                                        } else {
                                            android.util.Log.w("MainActivity", "⚠️ Mother code non trovato per $platformCode, uso picker manuale")
                                            pendingExtraction = Triple(archivePath, romTitle, romSlug)
                                            openExtractionFolderLauncher.launch(null)
                                        }
                                    } else {
                                        // Usa il picker manuale
                                        android.util.Log.d("MainActivity", "ℹ️ ES-DE non abilitato o path non configurato, uso picker manuale")
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
                android.util.Log.d("MainActivity", "✅ Path primary: $path")
                path
            } else {
                // SD card esterna o altro storage
                // Prova a ottenere il percorso usando StorageVolume
                val path = getExternalStoragePath(uri, type, relPath)
                if (path != null) {
                    android.util.Log.d("MainActivity", "✅ Path SD card: $path")
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
            android.util.Log.d("MainActivity", "🔍 Ricerca path per storageId=$storageId, relPath=$relPath")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: usa StorageVolume
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                val storageVolumes = storageManager.storageVolumes
                
                android.util.Log.d("MainActivity", "📦 Trovati ${storageVolumes.size} volumi di storage")
                
                for (volume in storageVolumes) {
                    val volumeUuid = volume.uuid
                    val volumePath = volume.directory?.path
                    
                    android.util.Log.d("MainActivity", "  - Volume: uuid=$volumeUuid, path=$volumePath, isRemovable=${volume.isRemovable}, isPrimary=${volume.isPrimary}")
                    
                    // Controlla se questo volume corrisponde allo storageId
                    // Il docId per SD card può essere l'UUID o un ID simile
                    if (volumeUuid != null && (volumeUuid == storageId || storageId.contains(volumeUuid) || volumeUuid.contains(storageId))) {
                        if (volumePath != null) {
                            val path = if (relPath.isNotEmpty()) "$volumePath/$relPath" else volumePath
                            android.util.Log.d("MainActivity", "✅ Trovato volume corrispondente: $volumeUuid -> $path")
                            return path
                        }
                    }
                }
                
                // Fallback 1: Prova il formato standard /storage/[storageId]
                val standardPath = "/storage/$storageId"
                val standardFile = java.io.File(standardPath)
                if (standardFile.exists() && standardFile.canRead()) {
                    val path = if (relPath.isNotEmpty()) "$standardPath/$relPath" else standardPath
                    android.util.Log.d("MainActivity", "✅ Path standard funziona: $path")
                    return path
                }
                
                // Fallback 2: Prova /mnt/media_rw/[storageId] (alcuni dispositivi)
                val mediaRwPath = "/mnt/media_rw/$storageId"
                val mediaRwFile = java.io.File(mediaRwPath)
                if (mediaRwFile.exists() && mediaRwFile.canRead()) {
                    val path = if (relPath.isNotEmpty()) "$mediaRwPath/$relPath" else mediaRwPath
                    android.util.Log.d("MainActivity", "✅ Path media_rw funziona: $path")
                    return path
                }
                
                // Fallback 3: Prova a ottenere il path dal URI usando MediaStore
                val path = getPathFromUri(uri)
                if (path != null) {
                    android.util.Log.d("MainActivity", "✅ Path ottenuto da URI: $path")
                    return path
                }
            } else {
                // Android < 10: prova metodi alternativi
                // Prova il formato standard
                val standardPath = "/storage/$storageId"
                val standardFile = java.io.File(standardPath)
                if (standardFile.exists()) {
                    val path = if (relPath.isNotEmpty()) "$standardPath/$relPath" else standardPath
                    android.util.Log.d("MainActivity", "✅ Path standard (Android < 10): $path")
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
