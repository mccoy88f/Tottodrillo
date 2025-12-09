package com.tottodrillo.presentation.components

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.DownloadListener
import android.webkit.CookieManager
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
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import com.tottodrillo.R
import com.tottodrillo.domain.model.DownloadLink
import com.tottodrillo.presentation.settings.SourceManagerEntryPoint
import dagger.hilt.EntryPoints
import kotlinx.coroutines.launch

/**
 * Dialog WebView headless per gestire download con JavaScript/countdown
 * Mostra la pagina con il countdown e intercetta il download quando parte
 */
@Composable
fun WebViewDownloadDialog(
    url: String,
    link: DownloadLink,
    onDownloadUrlExtracted: (String, DownloadLink, String) -> Unit, // Aggiunto parametro cookies
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Recupera i pattern di intercettazione dalla source metadata
    var interceptPatterns by remember { mutableStateOf<List<String>?>(null) }
    
    // Pattern di default (generici per ROM)
    val defaultPatterns = listOf(".nsp", ".xci", ".zip", ".7z")
    
    // Carica i pattern dalla source metadata
    LaunchedEffect(link.sourceId) {
        if (link.sourceId != null) {
            try {
                val entryPoint = EntryPoints.get(context, SourceManagerEntryPoint::class.java)
                val sourceManager = entryPoint.sourceManager()
                val metadata = sourceManager.getSourceMetadata(link.sourceId)
                interceptPatterns = metadata?.downloadInterceptPatterns
                android.util.Log.d("WebViewDownloadDialog", "📋 Pattern caricati per ${link.sourceId}: ${interceptPatterns}")
            } catch (e: Exception) {
                android.util.Log.w("WebViewDownloadDialog", "⚠️ Errore caricamento pattern per ${link.sourceId}: ${e.message}")
                interceptPatterns = null
            }
        } else {
            interceptPatterns = null
        }
    }
    
    // Combina pattern dalla source con pattern di default
    val allPatterns = remember(interceptPatterns) {
        (interceptPatterns ?: emptyList()) + defaultPatterns
    }

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
                
                // Header con titolo, pulsante indietro e pulsante chiudi (barra più stretta, stesso stile del modale info ROMs)
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
                            contentDescription = "Indietro"
                        )
                    }
                    
                    Text(
                        text = stringResource(R.string.webview_download_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
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
                                                        view?.loadUrl(popupUrl.toString())
                                                    }
                                                    return true // Intercetta la navigazione
                                                }
                                                
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    
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
                                                
                                                // Estrai i cookie dal popup WebView
                                                val popupCookieManager = CookieManager.getInstance()
                                                popupCookieManager.setAcceptCookie(true)
                                                val popupCookies = popupCookieManager.getCookie(url) ?: ""
                                                onDownloadUrlExtracted(url, updatedLink, popupCookies)
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
                                                Toast.makeText(context, context.getString(R.string.webview_popup_closed), Toast.LENGTH_LONG).show()
                                            }, 2000)
                                        }
                                        
                                        return true
                                    }
                                })
                                
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                        val currentUrl = view?.url
                                        val newUrl = request?.url?.toString()
                                        
                                        
                                        // Se c'è un popup aperto e la navigazione è verso un URL diverso dall'originale, bloccala
                                        if (popupOpen && newUrl != null && originalUrl != null && newUrl != originalUrl) {
                                            android.util.Log.d("WebViewDownloadDialog", "🚫 Bloccata navigazione principale (popup aperto): $newUrl")
                                            return true // Blocca la navigazione
                                        }
                                        
                                        // Se è un link di download diretto, intercettalo usando i pattern dalla source
                                        if (newUrl != null && allPatterns.any { pattern ->
                                            newUrl.contains(pattern) || newUrl.endsWith(pattern)
                                        }) {
                                            // Estrai i cookie dal WebView per il dominio principale e per l'URL di download
                                            val cookieManager = CookieManager.getInstance()
                                            cookieManager.setAcceptCookie(true)
                                            
                                            // Estrai cookie dal dominio principale (estratto dall'URL originale)
                                            val mainDomainCookies = try {
                                                val originalUrlObj = java.net.URL(url)
                                                val mainDomain = "${originalUrlObj.protocol}://${originalUrlObj.host}"
                                                cookieManager.getCookie(mainDomain) ?: ""
                                            } catch (e: Exception) {
                                                ""
                                            }
                                            
                                            // Estrai cookie dall'URL di download specifico
                                            val downloadUrlCookies = cookieManager.getCookie(newUrl) ?: ""
                                            
                                            // Estrai anche cookie dal dominio di download
                                            val downloadDomainCookies = try {
                                                val urlObj = java.net.URL(newUrl)
                                                val downloadDomain = "${urlObj.protocol}://${urlObj.host}"
                                                cookieManager.getCookie(downloadDomain) ?: ""
                                            } catch (e: Exception) {
                                                ""
                                            }
                                            
                                            // Combina i cookie (rimuovi duplicati mantenendo l'ordine: dominio principale ha priorità)
                                            val allCookies = mutableSetOf<String>()
                                            
                                            // Prima aggiungi i cookie del dominio principale (Cloudflare)
                                            if (mainDomainCookies.isNotEmpty()) {
                                                mainDomainCookies.split(";").forEach { cookie ->
                                                    val trimmed = cookie.trim()
                                                    if (trimmed.isNotEmpty()) {
                                                        allCookies.add(trimmed)
                                                    }
                                                }
                                            }
                                            
                                            // Poi aggiungi i cookie del dominio di download
                                            if (downloadDomainCookies.isNotEmpty()) {
                                                downloadDomainCookies.split(";").forEach { cookie ->
                                                    val trimmed = cookie.trim()
                                                    if (trimmed.isNotEmpty()) {
                                                        allCookies.add(trimmed)
                                                    }
                                                }
                                            }
                                            
                                            // Infine aggiungi i cookie specifici dell'URL
                                            if (downloadUrlCookies.isNotEmpty()) {
                                                downloadUrlCookies.split(";").forEach { cookie ->
                                                    val trimmed = cookie.trim()
                                                    if (trimmed.isNotEmpty()) {
                                                        allCookies.add(trimmed)
                                                    }
                                                }
                                            }
                                            
                                            val cookies = allCookies.joinToString("; ")
                                            onDownloadUrlExtracted(newUrl, link, cookies)
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
                                    // Estrai i cookie dal WebView per il dominio principale e per l'URL di download
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    
                                    // Estrai cookie dal dominio principale (estratto dall'URL originale)
                                    val mainDomainCookies = try {
                                        val originalUrlObj = java.net.URL(url)
                                        val mainDomain = "${originalUrlObj.protocol}://${originalUrlObj.host}"
                                        cookieManager.getCookie(mainDomain) ?: ""
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    
                                    // Estrai cookie dall'URL di download specifico
                                    val downloadUrlCookies = cookieManager.getCookie(url) ?: ""
                                    
                                    // Estrai anche cookie dal dominio di download (es. download.nswpediax.site per NSWpedia)
                                    val downloadDomainCookies = try {
                                        val urlObj = java.net.URL(url)
                                        val downloadDomain = "${urlObj.protocol}://${urlObj.host}"
                                        cookieManager.getCookie(downloadDomain) ?: ""
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    
                                    // Combina i cookie (rimuovi duplicati mantenendo l'ordine: dominio principale ha priorità)
                                    val allCookies = mutableSetOf<String>()
                                    
                                    // Prima aggiungi i cookie del dominio principale (Cloudflare)
                                    if (mainDomainCookies.isNotEmpty()) {
                                        mainDomainCookies.split(";").forEach { cookie ->
                                            val trimmed = cookie.trim()
                                            if (trimmed.isNotEmpty()) {
                                                allCookies.add(trimmed)
                                            }
                                        }
                                    }
                                    
                                    // Poi aggiungi i cookie del dominio di download
                                    if (downloadDomainCookies.isNotEmpty()) {
                                        downloadDomainCookies.split(";").forEach { cookie ->
                                            val trimmed = cookie.trim()
                                            if (trimmed.isNotEmpty()) {
                                                allCookies.add(trimmed)
                                            }
                                        }
                                    }
                                    
                                    // Infine aggiungi i cookie specifici dell'URL
                                    if (downloadUrlCookies.isNotEmpty()) {
                                        downloadUrlCookies.split(";").forEach { cookie ->
                                            val trimmed = cookie.trim()
                                            if (trimmed.isNotEmpty()) {
                                                allCookies.add(trimmed)
                                            }
                                        }
                                    }
                                    
                                    val cookies = allCookies.joinToString("; ")
                                    // Verifica se contiene cf_clearance (cookie Cloudflare)
                                    if (!cookies.contains("cf_clearance")) {
                                        android.util.Log.w("WebViewDownloadDialog", "Cookie cf_clearance NON trovato")
                                    }
                                    
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
                                    
                                    // Crea un nuovo link con il nome del file estratto se disponibile e i cookie
                                    val fileName = extractedFileName // Estrai in una val locale per evitare smart cast issues
                                    val updatedLink = if (fileName != null) {
                                        link.copy(name = fileName)
                                    } else {
                                        link
                                    }
                                    
                                    // Estrai l'URL finale del download usando i pattern dalla source
                                    val matchesPattern = allPatterns.any { pattern ->
                                        url.contains(pattern) || url.endsWith(pattern)
                                    }
                                    val matchesMimeType = mimetype?.contains("application/octet-stream") == true ||
                                        mimetype?.contains("application/x-") == true
                                    
                                    if (matchesPattern || matchesMimeType) {
                                        // URL finale trovato, passa anche i cookie al callback
                                        onDownloadUrlExtracted(url, updatedLink, cookies)
                                    } else {
                                        // Se l'URL non è quello finale, prova comunque (potrebbe essere un redirect)
                                        onDownloadUrlExtracted(url, updatedLink, cookies)
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

