package com.tottodrillo.presentation.components

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
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
                var originalUrl by remember { mutableStateOf<String?>(null) }
                var popupOpen by remember { mutableStateOf(false) }
                
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
                                
                                // Intercetta window.open per gestire i popup
                                setWebChromeClient(object : WebChromeClient() {
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: android.os.Message?
                                    ): Boolean {
                                        android.util.Log.d("WebViewDownloadDialog", "🔔 Popup richiesto, imposto flag")
                                        popupOpen = true
                                        
                                        // Sposta il lavoro pesante fuori dal main thread per evitare frame saltati
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            // Crea una nuova WebView per il popup (in background, invisibile)
                                            val newWebView = WebView(context)
                                            newWebView.settings.javaScriptEnabled = true
                                            newWebView.settings.domStorageEnabled = true
                                            newWebView.visibility = android.view.View.GONE // Nascondi il popup
                                            
                                            // Intercetta la navigazione del popup per caricare l'URL nel popup invece che nel principale
                                            newWebView.webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                    // Carica l'URL nel popup
                                                    request?.url?.let { popupUrl ->
                                                        android.util.Log.d("WebViewDownloadDialog", "🔗 Popup naviga a: $popupUrl")
                                                        view?.loadUrl(popupUrl.toString())
                                                    }
                                                    return true // Intercetta la navigazione
                                                }
                                                
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    android.util.Log.d("WebViewDownloadDialog", "✅ Popup caricato: $url")
                                                    
                                                    // Disabilita annunci anche nel popup
                                                    view?.evaluateJavascript(
                                                        """
                                                        (function() {
                                                            try {
                                                                window.adLink = null;
                                                                console.log('✅ Annunci popup disabilitati nel popup');
                                                            } catch(e) {
                                                                console.log('⚠️ Errore disabilitazione annunci:', e);
                                                            }
                                                        })();
                                                        """.trimIndent(),
                                                        null
                                                    )
                                                }
                                            }
                                            
                                            // Intercetta il download anche dal popup
                                            newWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                                android.util.Log.d("WebViewDownloadDialog", "📥 Download intercettato dal popup: $url")
                                                
                                                // Chiudi il popup e resetta il flag
                                                try {
                                                    (newWebView.parent as? android.view.ViewGroup)?.removeView(newWebView)
                                                } catch (e: Exception) {
                                                    // Popup già chiuso
                                                }
                                                popupOpen = false
                                                
                                                // Estrai il nome del file e avvia il download
                                                var extractedFileName: String? = null
                                                if (contentDisposition != null) {
                                                    val filenameMatch = Regex("filename[*]?=['\"]?([^'\"\\s;]+)['\"]?", RegexOption.IGNORE_CASE).find(contentDisposition)
                                                    if (filenameMatch != null) {
                                                        extractedFileName = filenameMatch.groupValues[1]
                                                        try {
                                                            extractedFileName = java.net.URLDecoder.decode(extractedFileName, "UTF-8")
                                                        } catch (e: Exception) {}
                                                    }
                                                }
                                                
                                                if (extractedFileName == null) {
                                                    try {
                                                        val urlPath = java.net.URL(url).path
                                                        val lastSegment = urlPath.substringAfterLast('/')
                                                        if (lastSegment.isNotEmpty() && lastSegment.contains('.')) {
                                                            extractedFileName = lastSegment
                                                        }
                                                    } catch (e: Exception) {}
                                                }
                                                
                                                val fileName = extractedFileName // Estrai in una val locale per evitare smart cast issues
                                                val updatedLink = if (fileName != null) {
                                                    link.copy(name = fileName)
                                                } else {
                                                    link
                                                }
                                                
                                                onDownloadUrlExtracted(url, updatedLink)
                                            }
                                            
                                            // Aggiungi il popup al parent (invisibile)
                                            (view?.parent as? android.view.ViewGroup)?.addView(newWebView)
                                            
                                            // Imposta la nuova WebView come destinazione del messaggio
                                            val transport = resultMsg?.obj as? android.webkit.WebView.WebViewTransport
                                            transport?.webView = newWebView
                                            resultMsg?.sendToTarget()
                                            
                                            // Chiudi automaticamente il popup dopo 2 secondi e mostra toast
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                try {
                                                    (newWebView.parent as? android.view.ViewGroup)?.removeView(newWebView)
                                                    android.util.Log.d("WebViewDownloadDialog", "🛑 Popup chiuso automaticamente")
                                                } catch (e: Exception) {
                                                    // Popup già chiuso
                                                }
                                                popupOpen = false
                                                // Mostra toast per dire all'utente di ricliccare
                                                Toast.makeText(context, "Popup chiuso. Ora puoi ricliccare su download", Toast.LENGTH_LONG).show()
                                            }, 2000)
                                        }
                                        
                                        return true
                                    }
                                })
                                
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                        val currentUrl = view?.url
                                        val newUrl = request?.url?.toString()
                                        
                                        android.util.Log.d("WebViewDownloadDialog", "🔗 Navigazione principale: $currentUrl -> $newUrl (popupOpen: $popupOpen)")
                                        
                                        // Se c'è un popup aperto e la navigazione è verso un URL diverso dall'originale, bloccala
                                        if (popupOpen && newUrl != null && originalUrl != null && newUrl != originalUrl) {
                                            android.util.Log.d("WebViewDownloadDialog", "🚫 Bloccata navigazione principale (popup aperto): $newUrl")
                                            return true // Blocca la navigazione
                                        }
                                        
                                        // Se è un link di download diretto, intercettalo
                                        if (newUrl != null && (
                                            newUrl.contains("sto.romsfast.com") || 
                                            newUrl.contains("?token=") || 
                                            newUrl.endsWith(".nsp") || 
                                            newUrl.endsWith(".xci") || 
                                            newUrl.endsWith(".zip") || 
                                            newUrl.endsWith(".7z")
                                        )) {
                                            android.util.Log.d("WebViewDownloadDialog", "📥 Link download diretto intercettato: $newUrl")
                                            onDownloadUrlExtracted(newUrl, link)
                                            return true
                                        }
                                        
                                        // Altrimenti, permetti la navigazione normale
                                        return false
                                    }
                                    
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        error = null
                                        canGoBack = view?.canGoBack() ?: false
                                        android.util.Log.d("WebViewDownloadDialog", "✅ Pagina principale caricata: $url")
                                        
                                        // Disabilita annunci popup iniettando JavaScript
                                        // Questo previene l'apertura di popup pubblicitari su alcuni siti (es. buzzheavier.com)
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                try {
                                                    window.adLink = null;
                                                    console.log('✅ Annunci popup disabilitati');
                                                } catch(e) {
                                                    console.log('⚠️ Errore disabilitazione annunci:', e);
                                                }
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    }
                                    
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        canGoBack = view?.canGoBack() ?: false
                                        
                                        // Salva l'URL originale se non è già stato salvato
                                        if (originalUrl == null && url != null) {
                                            originalUrl = url
                                            android.util.Log.d("WebViewDownloadDialog", "💾 URL originale salvato: $url")
                                        }
                                        
                                        android.util.Log.d("WebViewDownloadDialog", "🔄 Navigazione principale iniziata: $url")
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
                                    val fileName = extractedFileName // Estrai in una val locale per evitare smart cast issues
                                    val updatedLink = if (fileName != null) {
                                        link.copy(name = fileName)
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

                                // Imposta l'URL originale prima di caricare
                                originalUrl = url
                                android.util.Log.d("WebViewDownloadDialog", "💾 URL originale impostato: $url")
                                
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

