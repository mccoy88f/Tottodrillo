package com.tottodrillo.domain.model

/**
 * Configurazione per bypass Cloudflare
 */
data class CloudflareBypassConfig(
    val delaySeconds: Int = 0, // Secondi da attendere dopo il caricamento della pagina
    val extractUrlPattern: String? = null, // Pattern JavaScript per estrarre URL (opzionale)
    val cookieDomains: List<String>? = null // Domini da cui estrarre cookie (opzionale)
)

/**
 * Configurazione per WebView
 */
data class WebViewConfig(
    val delaySeconds: Int = 0, // Secondi da attendere prima di estrarre URL
    val extractUrlScript: String? = null, // Script JavaScript personalizzato per estrarre URL
    val interceptPatterns: List<String>? = null, // Pattern per intercettare download
    val requiresCookieExtraction: Boolean = true // Se estrarre cookie dal WebView
)

/**
 * Configurazione per HTTP Client personalizzato
 */
data class HttpClientConfig(
    val requiresSslTrustAll: Boolean = false, // Se accettare certificati SSL non validi
    val requiresCookieJar: Boolean = false, // Se usare CookieJar personalizzato
    val customHeaders: Map<String, String>? = null, // Header personalizzati
    val timeoutSeconds: Long = 30L // Timeout in secondi
)

/**
 * Configurazione SSL
 */
data class SslConfig(
    val trustAll: Boolean = false, // Se accettare tutti i certificati
    val customTrustManagers: List<String>? = null // Trust managers personalizzati (opzionale)
)

/**
 * Risultato del bypass Cloudflare
 */
data class CloudflareBypassResult(
    val success: Boolean,
    val finalUrl: String? = null,
    val cookies: String? = null,
    val originalUrl: String? = null,
    val error: String? = null
)

/**
 * Risultato dell'estrazione da WebView
 */
data class WebViewExtractionResult(
    val success: Boolean,
    val finalUrl: String? = null,
    val cookies: String? = null,
    val originalUrl: String? = null,
    val error: String? = null
)

