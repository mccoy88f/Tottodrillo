package com.crocdb.friends

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.crocdb.friends.presentation.downloads.DownloadsViewModel
import com.crocdb.friends.presentation.navigation.CrocdbNavGraph
import com.crocdb.friends.presentation.theme.CrocdbFriendsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity principale
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

    private var pendingExtraction: Triple<String, String, String>? = null // archivePath, romTitle, romSlug

    private val openExtractionFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val (archivePath, romTitle, romSlug) = pendingExtraction ?: return@registerForActivityResult
            uri?.let {
                val path = convertTreeUriToPath(it)
                if (path != null) {
                    downloadsViewModel.startExtraction(archivePath, path, romTitle, romSlug)
                }
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
            CrocdbFriendsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrocdbNavGraph(
                        initialRomSlug = pendingRomSlug,
                        onOpenDownloadFolderPicker = {
                            openDownloadFolderLauncher.launch(null)
                        },
                        onRequestExtraction = { archivePath, romTitle, romSlug ->
                            pendingExtraction = Triple(archivePath, romTitle, romSlug)
                            openExtractionFolderLauncher.launch(null)
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
     * Converte una tree URI (es. primary:Download) in un percorso filesystem
     * Funziona per lo storage "primary" (memoria interna principale).
     */
    private fun convertTreeUriToPath(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        if (parts.isEmpty()) return null

        val type = parts[0]
        val relPath = if (parts.size > 1) parts[1] else ""

        return if (type.equals("primary", ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory().path
            if (relPath.isNotEmpty()) "$base/$relPath" else base
        } else {
            null // Per ora gestiamo solo lo storage "primary"
        }
    }
}
