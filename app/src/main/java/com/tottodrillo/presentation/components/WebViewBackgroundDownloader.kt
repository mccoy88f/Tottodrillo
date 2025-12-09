package com.tottodrillo.presentation.components

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.DownloadListener
import android.util.Log
import com.tottodrillo.domain.model.DownloadLink
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper per gestire download da WebView in background (senza mostrare dialog)
 * Carica la pagina, attende (se necessario), estrae URL finale e cookie, poi avvia il download
 */
class WebViewBackgroundDownloader(
    private val context: Context
) {
    /**
     * Gestisce il download in background: carica la pagina, attende (se necessario), estrae URL e cookie
     * @param delaySeconds Secondi da attendere prima di estrarre l'URL (0 per nessun delay, specificato dalla source se necessario)
     */
    suspend fun handleDownloadInBackground(
        url: String,
        link: DownloadLink,
        delaySeconds: Int = 0,
        onDownloadReady: (finalUrl: String, cookies: String) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                visibility = android.view.View.GONE // Invisibile
                
                var isResumed = false // Flag per evitare doppio resume
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // Dopo che la pagina è caricata, attendi (se necessario) prima di estrarre l'URL finale
                        // Il delay è specificato dalla source (es. per challenge Cloudflare o altre validazioni server)
                        // Se delaySeconds = 0, il download viene intercettato immediatamente quando parte
                        val delayMs = if (delaySeconds > 0) delaySeconds * 1000L else 0L
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // Estrai l'URL finale dalla pagina usando JavaScript
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    try {
                                        // Cerca il link di download nella pagina
                                        var downloadLink = document.querySelector('#download-link');
                                        if (downloadLink) {
                                            return downloadLink.href;
                                        }
                                        // Prova anche altri selettori comuni
                                        var downloadBtn = document.querySelector('a[href*="download.nswpediax.site"]');
                                        if (downloadBtn) {
                                            return downloadBtn.href;
                                        }
                                        // Cerca qualsiasi link che contenga .nsp o .xci
                                        var links = document.querySelectorAll('a[href]');
                                        for (var i = 0; i < links.length; i++) {
                                            var href = links[i].href;
                                            if (href && (href.includes('.nsp') || href.includes('.xci'))) {
                                                return href;
                                            }
                                        }
                                        return null;
                                    } catch(e) {
                                        return null;
                                    }
                                })();
                                """.trimIndent()
                            ) { result ->
                                // Evita doppio resume
                                if (isResumed) {
                                    Log.w("WebViewBackgroundDownloader", "⚠️ Continuation già resumata, ignoro callback duplicato")
                                    return@evaluateJavascript
                                }
                                
                                val finalUrl = result?.removeSurrounding("\"")?.takeIf { it != "null" }
                                
                                if (finalUrl != null && finalUrl.isNotEmpty()) {
                                    
                                    // Estrai i cookie dal WebView
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
                                    val downloadUrlCookies = cookieManager.getCookie(finalUrl) ?: ""
                                    
                                    // Estrai anche cookie dal dominio di download
                                    val downloadDomainCookies = try {
                                        val urlObj = java.net.URL(finalUrl)
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
                                    
                                    // Avvia il download
                                    isResumed = true
                                    onDownloadReady(finalUrl, cookies)
                                    continuation.resume(Result.success(Unit))
                                } else {
                                    if (!isResumed) {
                                        Log.w("WebViewBackgroundDownloader", "⚠️ URL finale non trovato nella pagina${if (delaySeconds > 0) " dopo ${delaySeconds}s" else ""}")
                                        isResumed = true
                                        continuation.resume(Result.failure(Exception("URL finale non trovato nella pagina${if (delaySeconds > 0) " dopo l'attesa" else ""}")))
                                    }
                                }
                            }
                        }, delayMs)
                    }
                }
                
                // Intercetta anche il download listener come fallback (se l'utente clicca manualmente)
                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                    // Questo è un fallback, ma in background non dovrebbe essere necessario
                }
                
                // Carica l'URL
                loadUrl(url)
            }
            
            // Cleanup quando la coroutine viene cancellata
            continuation.invokeOnCancellation {
                try {
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                } catch (e: Exception) {
                    // Ignora errori di cleanup
                }
            }
        } catch (e: Exception) {
            Log.e("WebViewBackgroundDownloader", "❌ Errore nel gestire download in background", e)
            continuation.resume(Result.failure(e))
        }
    }
}

