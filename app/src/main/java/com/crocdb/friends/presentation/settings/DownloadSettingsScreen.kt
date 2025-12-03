package com.crocdb.friends.presentation.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.crocdb.friends.presentation.downloads.DownloadsViewModel

/**
 * Schermata impostazioni download
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    onNavigateBack: () -> Unit,
    onSelectFolder: () -> Unit,
    onSelectEsDeFolder: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val config by viewModel.downloadConfig.collectAsState()
    val showClearHistoryDialog by viewModel.showClearHistoryDialog.collectAsState()
    val context = LocalContext.current

    // Dialog di conferma per cancellare storico
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearHistoryDialog() },
            title = { Text("Cancella storico download ed estrazioni") },
            text = { 
                Text("Questa azione eliminerà tutti i file .status nella cartella di download predefinita. Questa operazione non può essere annullata.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearDownloadHistory() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancella")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearHistoryDialog() }) {
                    Text("Annulla")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
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
                text = "Percorso Download",
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
                            text = "Cartella di download",
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
                text = "Spazio disponibile: ${formatBytes(viewModel.getAvailableSpace(config.downloadPath))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Opzioni rete
            Text(
                text = "Rete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = "Solo WiFi",
                description = "Scarica solo quando connesso a WiFi",
                checked = config.useWifiOnly,
                onCheckedChange = viewModel::updateWifiOnly
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Notifiche
            Text(
                text = "Notifiche",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = "Notifiche",
                description = "Mostra notifiche durante e dopo download ed estrazioni",
                checked = config.notificationsEnabled,
                onCheckedChange = viewModel::updateNotifications
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Opzioni estrazione
            Text(
                text = "Estrazione",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = "Cancella file originale dopo l'estrazione",
                description = "Elimina il file scaricato dopo l'estrazione completata",
                checked = config.deleteArchiveAfterExtraction,
                onCheckedChange = viewModel::updateDeleteAfterExtract
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Compatibilità ES-DE
            Text(
                text = "Compatibilità ES-DE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItem(
                title = "Abilita compatibilità con ES-DE",
                description = "Estrai/sposta le ROM nella struttura cartelle di ES-DE",
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
                                text = "Cartella ROMs di ES-DE",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = config.esDeRomsPath ?: "Non selezionata",
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
                text = "Gestione Storico",
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
                        text = "Cancella storico download ed estrazioni",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Elimina tutti i file .status nella cartella di download predefinita",
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
                text = "Sorgenti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sorgente crocdb.net (sempre attiva)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "crocdb.net",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sorgente principale",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = null, // Non disattivabile
                    enabled = false
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
                    text = "Versione $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Autore: McCoy88f",
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
                        text = "GitHub",
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
                    text = "Supportami",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Se ti piace questo progetto, sostienilo con ora!",
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
                        text = "Buy me a coffee 🍺",
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
                        text = "PayPal 🍻",
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
