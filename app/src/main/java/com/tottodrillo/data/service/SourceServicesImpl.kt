package com.tottodrillo.data.service

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import com.tottodrillo.domain.model.CloudflareBypassConfig
import com.tottodrillo.domain.model.CloudflareBypassResult
import com.tottodrillo.domain.model.HttpClientConfig
import com.tottodrillo.domain.model.SslConfig
import com.tottodrillo.domain.model.WebViewConfig
import com.tottodrillo.domain.model.WebViewExtractionResult
import com.tottodrillo.domain.service.SourceServices
import com.tottodrillo.domain.manager.SourceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

/**
 * Implementazione dei servizi avanzati per le sorgenti
 * Centralizza tutta la logica per bypass Cloudflare, gestione cookie, WebView, SSL, ecc.
 */
@Singleton
class SourceServicesImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager,
    private val baseOkHttpClient: OkHttpClient
) : SourceServices {
    
    // Cache per CookieJar per sorgente
    private val cookieJars = mutableMapOf<String, CookieJar>()
    
    // Cache per SSL Context
    private var sslContext: SSLContext? = null
    private var trustAllCerts: Array<TrustManager>? = null
    
    override fun createHttpClient(
        sourceId: String,
        config: HttpClientConfig
    ): OkHttpClient {
        val builder = baseOkHttpClient.newBuilder()
        
        // Configura SSL se richiesto
        if (config.requiresSslTrustAll) {
            val sslContext = createSslContext(SslConfig(trustAll = true))
            val trustManager = trustAllCerts?.get(0) as? X509TrustManager
            if (sslContext != null && trustManager != null) {
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
                builder.hostnameVerifier { _, _ -> true }
            }
        }
        
        // Configura CookieJar se richiesto
        if (config.requiresCookieJar) {
            builder.cookieJar(createCookieManager(sourceId))
        }
        
        // Aggiungi header personalizzati
        if (config.customHeaders != null && config.customHeaders.isNotEmpty()) {
            builder.addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    config.customHeaders.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }.build()
                chain.proceed(request)
            }
        }
        
        // Configura timeout
        builder.connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        builder.readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        builder.writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        
        return builder.build()
    }
    
    override suspend fun bypassCloudflare(
        url: String,
        sourceId: String,
        config: CloudflareBypassConfig
    ): CloudflareBypassResult = withContext(Dispatchers.IO) {
        try {
            // Usa extractUrlFromWebView per il bypass Cloudflare
            val webViewConfig = WebViewConfig(
                delaySeconds = config.delaySeconds,
                extractUrlScript = config.extractUrlPattern,
                interceptPatterns = null,
                requiresCookieExtraction = true
            )
            
            val result = extractUrlFromWebView(url, sourceId, webViewConfig)
            
            CloudflareBypassResult(
                success = result.success,
                finalUrl = result.finalUrl,
                cookies = result.cookies,
                originalUrl = result.originalUrl,
                error = result.error
            )
        } catch (e: Exception) {
            Log.e("SourceServicesImpl", "Errore nel bypass Cloudflare per $sourceId", e)
            CloudflareBypassResult(
                success = false,
                error = e.message ?: "Errore sconosciuto"
            )
        }
    }
    
    override fun createCookieManager(sourceId: String): CookieJar {
        return cookieJars.getOrPut(sourceId) {
            object : CookieJar {
                private val cookies = mutableListOf<Cookie>()
                
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    this.cookies.addAll(cookies)
                }
                
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookies.filter { it.matches(url) }
                }
            }
        }
    }
    
    override suspend fun extractUrlFromWebView(
        url: String,
        sourceId: String,
        config: WebViewConfig
    ): WebViewExtractionResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            try {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    visibility = android.view.View.GONE // Invisibile
                    
                    var isResumed = false
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            
                            val delayMs = if (config.delaySeconds > 0) config.delaySeconds * 1000L else 0L
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                // Usa script personalizzato o script di default
                                val extractScript = config.extractUrlScript ?: getDefaultExtractScript(config.interceptPatterns)
                                
                                view?.evaluateJavascript(extractScript) { result ->
                                    if (isResumed) {
                                        return@evaluateJavascript
                                    }
                                    
                                    val finalUrl = result?.removeSurrounding("\"")?.takeIf { it != "null" && it.isNotEmpty() }
                                    
                                    if (finalUrl != null && finalUrl.isNotEmpty()) {
                                        val cookies = if (config.requiresCookieExtraction) {
                                            extractCookiesFromWebView(url, finalUrl)
                                        } else {
                                            ""
                                        }
                                        
                                        isResumed = true
                                        continuation.resume(
                                            WebViewExtractionResult(
                                                success = true,
                                                finalUrl = finalUrl,
                                                cookies = cookies,
                                                originalUrl = url
                                            )
                                        )
                                    } else {
                                        if (!isResumed) {
                                            isResumed = true
                                            continuation.resume(
                                                WebViewExtractionResult(
                                                    success = false,
                                                    error = "URL finale non trovato nella pagina${if (config.delaySeconds > 0) " dopo ${config.delaySeconds}s" else ""}"
                                                )
                                            )
                                        }
                                    }
                                }
                            }, delayMs)
                        }
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
                Log.e("SourceServicesImpl", "Errore nell'estrazione URL da WebView per $sourceId", e)
                continuation.resume(
                    WebViewExtractionResult(
                        success = false,
                        error = e.message ?: "Errore sconosciuto"
                    )
                )
            }
        }
    }
    
    override fun createSslContext(config: SslConfig): SSLContext? {
        if (!config.trustAll) {
            return null
        }
        
        // Usa cache se già creato
        if (sslContext != null && trustAllCerts != null) {
            return sslContext
        }
        
        return try {
            trustAllCerts = arrayOf(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )
            
            val context = SSLContext.getInstance("SSL")
            context.init(null, trustAllCerts, java.security.SecureRandom())
            sslContext = context
            context
        } catch (e: Exception) {
            Log.e("SourceServicesImpl", "Errore nella creazione SSL Context", e)
            null
        }
    }
    
    override fun getBaseHttpClient(sourceId: String): OkHttpClient {
        // Ritorna il client base con eventuale Referer interceptor se configurato
        val builder = baseOkHttpClient.newBuilder()
        
        // Aggiungi Referer interceptor se la sorgente ha imageRefererPattern
        try {
            val metadata = kotlinx.coroutines.runBlocking {
                sourceManager.getSourceMetadata(sourceId)
            }
            
            val refererPattern = metadata?.imageRefererPattern
            if (refererPattern != null && refererPattern.contains("{id}")) {
                builder.addInterceptor(createRefererInterceptor(refererPattern, sourceId))
            }
        } catch (e: Exception) {
            Log.w("SourceServicesImpl", "Errore nel recupero metadata per Referer: ${e.message}")
        }
        
        return builder.build()
    }
    
    /**
     * Estrae cookie dal WebView per una URL
     */
    private fun extractCookiesFromWebView(originalUrl: String, finalUrl: String): String {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        
        val allCookies = mutableSetOf<String>()
        
        // Estrai cookie dal dominio principale
        try {
            val originalUrlObj = java.net.URL(originalUrl)
            val mainDomain = "${originalUrlObj.protocol}://${originalUrlObj.host}"
            val mainDomainCookies = cookieManager.getCookie(mainDomain) ?: ""
            if (mainDomainCookies.isNotEmpty()) {
                mainDomainCookies.split(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) {
                        allCookies.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignora errori
        }
        
        // Estrai cookie dal dominio di download
        try {
            val urlObj = java.net.URL(finalUrl)
            val downloadDomain = "${urlObj.protocol}://${urlObj.host}"
            val downloadDomainCookies = cookieManager.getCookie(downloadDomain) ?: ""
            if (downloadDomainCookies.isNotEmpty()) {
                downloadDomainCookies.split(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) {
                        allCookies.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignora errori
        }
        
        // Estrai cookie specifici dell'URL
        val downloadUrlCookies = cookieManager.getCookie(finalUrl) ?: ""
        if (downloadUrlCookies.isNotEmpty()) {
            downloadUrlCookies.split(";").forEach { cookie ->
                val trimmed = cookie.trim()
                if (trimmed.isNotEmpty()) {
                    allCookies.add(trimmed)
                }
            }
        }
        
        return allCookies.joinToString("; ")
    }
    
    /**
     * Crea script JavaScript di default per estrarre URL
     */
    private fun getDefaultExtractScript(interceptPatterns: List<String>?): String {
        val patterns = interceptPatterns ?: listOf(".nsp", ".xci", ".zip", ".7z")
        val patternsJs = patterns.joinToString(" || ") { "href.includes('$it')" }
        
        return """
            (function() {
                try {
                    // Cerca il link di download nella pagina
                    var downloadLink = document.querySelector('#download-link');
                    if (downloadLink) {
                        return downloadLink.href;
                    }
                    // Cerca qualsiasi link che corrisponda ai pattern
                    var links = document.querySelectorAll('a[href]');
                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].href;
                        if (href && ($patternsJs)) {
                            return href;
                        }
                    }
                    return null;
                } catch(e) {
                    return null;
                }
            })();
        """.trimIndent()
    }
    
    /**
     * Crea interceptor per aggiungere Referer header alle immagini
     */
    private fun createRefererInterceptor(refererPattern: String, sourceId: String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val url = request.url
            
            var newRequest = request
            
            // Estrai l'ID dall'URL dell'immagine
            val imageId = url.queryParameter("id")
                ?: url.pathSegments.lastOrNull()?.takeIf { it.matches(Regex("\\d+")) }
                ?: url.toString()
                    .substringAfterLast("/")
                    .substringBefore("?")
                    .takeIf { it.matches(Regex("\\d+")) }
            
            if (imageId != null && imageId.isNotBlank()) {
                val refererUrl = refererPattern.replace("{id}", imageId)
                newRequest = request.newBuilder()
                    .header("Referer", refererUrl)
                    .build()
                Log.d("SourceServicesImpl", "Aggiunto Referer per $sourceId: $refererUrl")
            }
            
            chain.proceed(newRequest)
        }
    }
}

