package com.tottodrillo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Application class principale
 * Annotata con @HiltAndroidApp per abilitare Hilt dependency injection
 */
@HiltAndroidApp
class TottodrilloApp : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var sourceManager: com.tottodrillo.domain.manager.SourceManager

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
     * Configura ImageLoader globale per Coil con supporto SVG e CookieJar per Vimm's Lair
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
                // Per Vimm's Lair, se stiamo caricando un'immagine, visita prima la pagina ROM
                // per ottenere i cookie di sessione (solo una volta per ROM)
                if (url.host.contains("vimm.net") && url.encodedPath.contains("/image.php")) {
                    // Estrai l'ID della ROM dall'URL dell'immagine
                    val romId = url.queryParameter("id")
                    if (romId != null && !visitedRomPages.contains(romId)) {
                        visitedRomPages.add(romId)
                        // Visita la pagina ROM per ottenere i cookie (usa un client separato per evitare loop)
                        val romPageUrl = "https://vimm.net/vault/$romId".toHttpUrl()
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
        
        // Configura SSL per accettare certificati (necessario per Vimm's Lair)
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
            // Usa try-catch per gestire il caso in cui SourceManager non sia ancora inizializzato
            try {
                if (::sourceManager.isInitialized) {
                    val installedSources = kotlinx.coroutines.runBlocking {
                        sourceManager.getInstalledSources()
                    }
                    
                    for (source in installedSources) {
                        val metadata = kotlinx.coroutines.runBlocking {
                            sourceManager.getSourceMetadata(source.id)
                        }
                        
                        val refererPattern = metadata?.imageRefererPattern
                        if (refererPattern != null) {
                            // Verifica se l'URL corrisponde a questa sorgente (controlla host o pattern)
                            val sourceHost = metadata.baseUrl?.let { baseUrl ->
                                try { 
                                    baseUrl.toHttpUrl().host
                                } catch (e: Exception) { 
                                    null 
                                }
                            }
                            
                            if (sourceHost != null && sourceHost.isNotBlank() && url.host.contains(sourceHost, ignoreCase = true)) {
                                // Estrai l'ID dall'URL dell'immagine (cerca pattern comuni come ?id=, /id/, etc.)
                                val imageId = url.queryParameter("id") 
                                    ?: url.pathSegments.lastOrNull()
                                    ?: url.toString().substringAfterLast("/").substringBefore("?")
                                
                                if (imageId != null && imageId.isNotBlank()) {
                                    // Sostituisci {id} nel pattern con l'ID reale
                                    val refererUrl = refererPattern.replace("{id}", imageId)
                                    newRequest = request.newBuilder()
                                        .header("Referer", refererUrl)
                                        .build()
                                    android.util.Log.d("TottodrilloApp", "Aggiunto Referer per ${source.id}: $refererUrl")
                                    break
                                }
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
            }
            .okHttpClient(okHttpClient)
            .build()
    }
}
