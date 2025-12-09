package com.tottodrillo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import com.tottodrillo.data.cache.CachedImageFetcher
import com.tottodrillo.data.repository.RomCacheManager
import com.tottodrillo.data.repository.DownloadConfigRepository
import com.google.gson.Gson

/**
 * Application class principale
 * Annotata con @HiltAndroidApp per abilitare Hilt dependency injection
 */
@HiltAndroidApp
class TottodrilloApp : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var sourceManager: com.tottodrillo.domain.manager.SourceManager
    
    // Cache manager per immagini (inizializzato lazy)
    // Nota: Non possiamo usare @Inject qui perché l'ImageLoader viene creato prima che Hilt sia completamente inizializzato
    private val cacheManager: RomCacheManager by lazy {
        // Inizializza il cache manager manualmente
        val configRepository = com.tottodrillo.data.repository.DownloadConfigRepository(this)
        val gson = com.google.gson.GsonBuilder().setLenient().create()
        RomCacheManager(this, configRepository, gson)
    }

    override fun onCreate() {
        super.onCreate()
        
        // Inizializza Chaquopy per supporto Python nelle sorgenti
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        
        // Inizializzazione app
        // Qui possiamo aggiungere configurazioni come:
        // - Analytics
        // - Crash reporting
        // - WorkManager
        // - Database Room
    }
    
    /**
     * Configura ImageLoader globale per Coil con supporto SVG e CookieJar per sorgenti che richiedono cookie
     */
    override fun newImageLoader(): ImageLoader {
        // Nota: SourceManager viene iniettato da Hilt, ma potrebbe non essere disponibile
        // al momento della creazione dell'ImageLoader. Usiamo un approccio lazy.
        
        // CookieJar in memoria per gestire i cookie di sessione per le immagini
        val cookieJar = object : CookieJar {
            val visitedRomPages = mutableSetOf<String>() // Cache per ROM già visitate (pubblico per l'interceptor)
            private val cookies = mutableListOf<Cookie>()
            
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this.cookies.addAll(cookies)
            }
            
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                // Per sorgenti che richiedono cookie di sessione per le immagini, visita prima la pagina ROM
                // per ottenere i cookie (solo una volta per ROM)
                if (url.encodedPath.contains("/image.php")) {
                    // Estrai l'ID della ROM dall'URL dell'immagine
                    val romId = url.queryParameter("id")
                    if (romId != null && !visitedRomPages.contains(romId)) {
                        visitedRomPages.add(romId)
                        // Costruisci l'URL della pagina ROM estraendo il dominio base
                        val baseUrl = "${url.scheme}://${url.host}"
                        val romPageUrl = "$baseUrl/vault/$romId".toHttpUrl()
                        try {
                            // Usa un client temporaneo con gestione SSL per visitare la pagina ROM
                            // Nota: In produzione, dovresti usare certificati validi
                            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                                object : javax.net.ssl.X509TrustManager {
                                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                                }
                            )
                            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                            val sslSocketFactory = sslContext.socketFactory
                            
                            val tempClient = OkHttpClient.Builder()
                                .cookieJar(this) // Usa lo stesso CookieJar per salvare i cookie
                                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                                .hostnameVerifier { _, _ -> true } // Accetta tutti gli hostname
                                .build()
                            val request = okhttp3.Request.Builder()
                                .url(romPageUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                .build()
                            tempClient.newCall(request).execute().use {
                                // I cookie vengono salvati automaticamente dal CookieJar
                            }
                        } catch (e: Exception) {
                            // Ignora errori, usa i cookie esistenti
                            android.util.Log.w("TottodrilloApp", "Errore nel visitare pagina ROM per cookie: ${e.message}")
                        }
                    }
                }
                return cookies.filter { it.matches(url) }
            }
        }
        
        // Configura SSL per accettare certificati (necessario per alcune sorgenti con certificati self-signed)
        // Nota: In produzione, dovresti usare certificati validi
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        val sslSocketFactory = sslContext.socketFactory
        
        // Interceptor per aggiungere Referer header alle immagini se richiesto dalla source
        val refererInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            val url = request.url
            
            var newRequest = request
            
            // Controlla se questa è una richiesta di immagine che richiede Referer
            // L'interceptor è completamente generico e si basa solo sulla configurazione della sorgente
            // (imageRefererPattern nel source.json), senza logica hardcoded per sorgenti specifiche
            try {
                if (::sourceManager.isInitialized) {
                    val installedSources = kotlinx.coroutines.runBlocking {
                        sourceManager.getInstalledSources()
                    }
                    
                    // Prova ogni sorgente installata per vedere se ha un pattern Referer configurato
                    for (source in installedSources) {
                        val metadata = kotlinx.coroutines.runBlocking {
                            sourceManager.getSourceMetadata(source.id)
                        }
                        
                        val refererPattern = metadata?.imageRefererPattern
                        if (refererPattern != null && refererPattern.contains("{id}")) {
                            // Estrai l'ID dall'URL dell'immagine in vari modi possibili:
                            // 1. Query parameter "id" (es. ?id=123)
                            // 2. Ultimo segmento del path se è numerico (es. /image/123)
                            // 3. Parte finale dell'URL prima dei query params se è numerica
                            val imageId = url.queryParameter("id") 
                                ?: url.pathSegments.lastOrNull()?.takeIf { it.matches(Regex("\\d+")) }
                                ?: url.toString()
                                    .substringAfterLast("/")
                                    .substringBefore("?")
                                    .takeIf { it.matches(Regex("\\d+")) }
                            
                            if (imageId != null && imageId.isNotBlank()) {
                                // Sostituisci {id} nel pattern con l'ID estratto
                                val refererUrl = refererPattern.replace("{id}", imageId)
                                newRequest = request.newBuilder()
                                    .header("Referer", refererUrl)
                                    .build()
                                android.util.Log.d("TottodrilloApp", "Aggiunto Referer per ${source.id}: $refererUrl")
                                break // Usa la prima sorgente che corrisponde
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Se SourceManager non è disponibile, procedi senza Referer
                android.util.Log.w("TottodrilloApp", "Errore nel recupero metadata per Referer: ${e.message}")
            }
            
            chain.proceed(newRequest)
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(refererInterceptor)
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true } // Accetta tutti gli hostname
            .build()
        
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                // Aggiungi il fetcher personalizzato per la cache delle immagini
                add(CachedImageFetcher.Factory(cacheManager, okHttpClient))
            }
            .okHttpClient(okHttpClient)
            .allowHardware(false) // Disabilita hardware bitmap per evitare problemi con alcuni formati
            .build()
    }
}
