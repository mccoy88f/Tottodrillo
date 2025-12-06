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
                // Header con titolo e pulsante chiudi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preparazione download...",
                        style = MaterialTheme.typography.titleLarge
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
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        error = null
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
                                    // Estrai l'URL finale del download
                                    if (url.contains("sto.romsfast.com") || url.contains("?token=")) {
                                        // URL finale trovato, chiudi il dialog e avvia il download
                                        onDownloadUrlExtracted(url, link)
                                    } else {
                                        // Se l'URL non è quello finale, prova comunque
                                        onDownloadUrlExtracted(url, link)
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
                    text = "Attendere il completamento del countdown. Il download partirà automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

