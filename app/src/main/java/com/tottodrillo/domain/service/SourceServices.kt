package com.tottodrillo.domain.service

import com.tottodrillo.domain.model.CloudflareBypassConfig
import com.tottodrillo.domain.model.CloudflareBypassResult
import com.tottodrillo.domain.model.HttpClientConfig
import com.tottodrillo.domain.model.SslConfig
import com.tottodrillo.domain.model.WebViewConfig
import com.tottodrillo.domain.model.WebViewExtractionResult
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import javax.net.ssl.SSLContext

/**
 * Interfaccia che espone servizi avanzati per le sorgenti
 * L'app principale implementa questa interfaccia per fornire funzionalità
 * come bypass Cloudflare, gestione cookie, WebView helper, ecc.
 * 
 * Le sorgenti possono richiedere questi servizi tramite configurazione
 * nel SourceMetadata, rendendo l'app completamente agnostica dalle sorgenti.
 */
interface SourceServices {
    /**
     * Crea un HTTP Client personalizzato per una sorgente
     * @param sourceId ID della sorgente
     * @param config Configurazione per il client HTTP
     * @return OkHttpClient configurato secondo le richieste della sorgente
     */
    fun createHttpClient(
        sourceId: String,
        config: HttpClientConfig
    ): OkHttpClient
    
    /**
     * Esegue bypass Cloudflare per una URL
     * @param url URL da bypassare
     * @param sourceId ID della sorgente
     * @param config Configurazione per il bypass
     * @return Risultato con URL finale e cookie
     */
    suspend fun bypassCloudflare(
        url: String,
        sourceId: String,
        config: CloudflareBypassConfig
    ): CloudflareBypassResult
    
    /**
     * Crea un CookieManager per una sorgente
     * @param sourceId ID della sorgente
     * @return CookieJar personalizzato per la sorgente
     */
    fun createCookieManager(sourceId: String): CookieJar
    
    /**
     * Estrae URL finale da una pagina WebView (utile per bypass Cloudflare o pagine intermedie)
     * @param url URL iniziale da caricare
     * @param sourceId ID della sorgente
     * @param config Configurazione per il WebView
     * @return Risultato con URL finale e cookie estratti
     */
    suspend fun extractUrlFromWebView(
        url: String,
        sourceId: String,
        config: WebViewConfig
    ): WebViewExtractionResult
    
    /**
     * Crea un SSL Context personalizzato
     * @param config Configurazione SSL
     * @return SSLContext configurato, o null se non necessario (es. trustAll = false)
     */
    fun createSslContext(config: SslConfig): SSLContext?
    
    /**
     * Ottiene un HTTP Client base con configurazione standard
     * Utile quando una sorgente non richiede configurazioni speciali
     * @param sourceId ID della sorgente
     * @return OkHttpClient base
     */
    fun getBaseHttpClient(sourceId: String): OkHttpClient
}

