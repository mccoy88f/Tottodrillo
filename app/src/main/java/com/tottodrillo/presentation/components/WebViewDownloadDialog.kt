package com.tottodrillo.presentation.components

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.DownloadListener
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import android.widget.Toast
import com.tottodrillo.domain.model.DownloadLink

/**
 * Dialog WebView headless per gestire download con JavaScript/countdown
 * Mostra la pagina con il countdown e intercetta il download quando parte
 */
@Composable
fun WebViewDownloadDialog(
    url: String,
    link: DownloadLink,
    onDownloadUrlExtracted: (String, DownloadLink) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Riferimento al WebView per navigazione indietro
                var webViewRef by remember { mutableStateOf<WebView?>(null) }
                var canGoBack by remember { mutableStateOf(false) }
                
                // Header con titolo, pulsante indietro e pulsante chiudi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsante indietro (a sinistra)
                    IconButton(
                        onClick = {
                            webViewRef?.goBack()
                        },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                    
                    Text(
                        text = "Avvio download manuale",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Divider()

                // WebView
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                canGoBack = this.canGoBack()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        error = null
                                        canGoBack = view?.canGoBack() ?: false
                                    }
                                    
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        canGoBack = view?.canGoBack() ?: false
                                    }
                                    

                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        super.onReceivedError(view, errorCode, description, failingUrl)
                                        isLoading = false
                                        error = description ?: "Errore nel caricamento della pagina"
                                    }
                                }

                                // Intercetta il download quando parte
                                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                    // Estrai il nome del file da contentDisposition se disponibile
                                    var extractedFileName: String? = null
                                    if (contentDisposition != null) {
                                        // Pattern: attachment; filename="nomefile.nsp" o attachment; filename*=UTF-8''nomefile.nsp
                                        val filenameMatch = Regex("filename[*]?=['\"]?([^'\"\\s;]+)['\"]?", RegexOption.IGNORE_CASE).find(contentDisposition)
                                        if (filenameMatch != null) {
                                            extractedFileName = filenameMatch.groupValues[1]
                                            // Decodifica URL encoding se presente
                                            try {
                                                extractedFileName = java.net.URLDecoder.decode(extractedFileName, "UTF-8")
                                            } catch (e: Exception) {
                                                // Ignora errori di decodifica
                                            }
                                        }
                                    }
                                    
                                    // Se non trovato in contentDisposition, prova a estrarre dall'URL
                                    if (extractedFileName == null) {
                                        try {
                                            val urlPath = java.net.URL(url).path
                                            val lastSegment = urlPath.substringAfterLast('/')
                                            if (lastSegment.isNotEmpty() && lastSegment.contains('.')) {
                                                extractedFileName = lastSegment
                                            }
                                        } catch (e: Exception) {
                                            // Ignora errori
                                        }
                                    }
                                    
                                    // Crea un nuovo link con il nome del file estratto se disponibile
                                    val updatedLink = if (extractedFileName != null) {
                                        link.copy(name = extractedFileName)
                                    } else {
                                        link
                                    }
                                    
                                    // Estrai l'URL finale del download
                                    if (url.contains("sto.romsfast.com") || url.contains("?token=") || url.endsWith(".nsp") || url.endsWith(".xci") || url.endsWith(".zip") || url.endsWith(".7z")) {
                                        // URL finale trovato, chiudi il dialog e avvia il download
                                        onDownloadUrlExtracted(url, updatedLink)
                                    } else {
                                        // Se l'URL non è quello finale, prova comunque
                                        onDownloadUrlExtracted(url, updatedLink)
                                    }
                                }

                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Loading indicator
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Error message
                    error?.let { errorMsg ->
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onDismiss) {
                                Text("Chiudi")
                            }
                        }
                    }
                }

                // Footer con informazioni
                Divider()
                Text(
                    text = "Procedi manualmente al download, se si apre un pop up torna indietro e riprova.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

