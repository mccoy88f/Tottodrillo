package com.tottodrillo.presentation.components

import android.content.Context
import android.util.Log
import com.tottodrillo.domain.model.DownloadLink
import com.tottodrillo.domain.model.WebViewConfig
import com.tottodrillo.domain.service.SourceServices
import com.tottodrillo.presentation.settings.SourceManagerEntryPoint
import dagger.hilt.EntryPoints
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper per gestire download da WebView in background (senza mostrare dialog)
 * Usa SourceServices per estrarre URL e cookie dalla pagina WebView
 */
class WebViewBackgroundDownloader(
    private val context: Context,
    private val sourceServices: SourceServices? = null
) {
    /**
     * Gestisce il download in background: carica la pagina, attende (se necessario), estrae URL e cookie
     * @param delaySeconds Secondi da attendere prima di estrarre l'URL (0 per nessun delay, specificato dalla source se necessario)
     */
    suspend fun handleDownloadInBackground(
        url: String,
        link: DownloadLink,
        delaySeconds: Int = 0,
        onDownloadReady: (finalUrl: String, cookies: String, originalUrl: String?) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            // Se SourceServices è disponibile, usalo per estrarre URL e cookie
            if (sourceServices != null && link.sourceId != null) {
                // Recupera i pattern dalla sorgente se disponibili
                val interceptPatterns = try {
                    val entryPoint = EntryPoints.get(context, SourceManagerEntryPoint::class.java)
                    val sourceManager = entryPoint.sourceManager()
                    val metadata = sourceManager.getSourceMetadata(link.sourceId)
                    metadata?.downloadInterceptPatterns
                } catch (e: Exception) {
                    Log.w("WebViewBackgroundDownloader", "⚠️ Errore nel recupero pattern dalla sorgente: ${e.message}")
                    null
                }
                
                val webViewConfig = WebViewConfig(
                    delaySeconds = delaySeconds,
                    extractUrlScript = null, // Usa script di default
                    interceptPatterns = interceptPatterns, // Pattern dalla sorgente o null (userà default in SourceServicesImpl)
                    requiresCookieExtraction = true
                )
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val result = sourceServices.extractUrlFromWebView(url, link.sourceId!!, webViewConfig)
                    
                    if (result.success && result.finalUrl != null) {
                        onDownloadReady(
                            result.finalUrl!!,
                            result.cookies ?: "",
                            result.originalUrl
                        )
                        continuation.resume(Result.success(Unit))
                    } else {
                        Log.w("WebViewBackgroundDownloader", "⚠️ Estrazione URL fallita: ${result.error}")
                        continuation.resume(Result.failure(Exception(result.error ?: "Errore sconosciuto")))
                    }
                }
            } else {
                // Fallback: usa implementazione legacy se SourceServices non è disponibile
                Log.w("WebViewBackgroundDownloader", "⚠️ SourceServices non disponibile, uso implementazione legacy")
                continuation.resume(Result.failure(Exception("SourceServices non disponibile. Assicurati di passare SourceServices al costruttore.")))
            }
        } catch (e: Exception) {
            Log.e("WebViewBackgroundDownloader", "❌ Errore nel gestire download in background", e)
            continuation.resume(Result.failure(e))
        }
    }
}

