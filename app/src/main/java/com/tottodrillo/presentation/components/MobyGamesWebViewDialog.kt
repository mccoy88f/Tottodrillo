package com.tottodrillo.presentation.components

import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import com.tottodrillo.R

/**
 * Dialog WebView per la ricerca info ROMs (MobyGames o Gamefaqs)
 */
@Composable
fun MobyGamesWebViewDialog(
    url: String,
    title: String = stringResource(R.string.rom_info_search_title),
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Estrai le stringhe nel contesto composable
    val backText = stringResource(R.string.back)
    val closeText = stringResource(R.string.close)
    val errorText = stringResource(R.string.rom_info_search_error)

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
                
                // Header con titolo, pulsante indietro e pulsante chiudi (barra più stretta)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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
                            contentDescription = backText
                        )
                    }
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    // Pulsante chiudi (a destra) - stesso stile del WebViewDownloadDialog
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
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
                                        error = description ?: errorText
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
                                Text(closeText)
                            }
                        }
                    }
                }
                
                // Footer (stesso stile del WebViewDownloadDialog ma senza testo informativo)
                // Non necessario per MobyGames, ma manteniamo la struttura simile
            }
        }
    }
}

