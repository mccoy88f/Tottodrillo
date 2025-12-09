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
     * Configura ImageLoader globale per Coil con supporto SVG
     * Usa SourceServices per gestire Referer e SSL se necessario
     * Nota: L'ImageLoader viene creato prima che Hilt sia completamente inizializzato,
     * quindi usiamo un approccio semplificato con SourceManager già iniettato
     */
    override fun newImageLoader(): ImageLoader {
        // CookieJar semplificato per immagini (senza logica complessa di visita pagine)
        val cookieJar = object : CookieJar {
            private val cookies = mutableListOf<Cookie>()
            
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this.cookies.addAll(cookies)
            }
            
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
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
        // La logica è generica e basata su imageRefererPattern nel metadata
        val refererInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            val url = request.url
            
            var newRequest = request
            
            // Controlla se questa è una richiesta di immagine che richiede Referer
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
                                android.util.Log.d("TottodrilloApp", "Aggiunto Referer per ${source.id}: $refererUrl")
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("TottodrilloApp", "Errore nel recupero metadata per Referer: ${e.message}")
            }
            
            chain.proceed(newRequest)
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(refererInterceptor)
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
        
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                add(CachedImageFetcher.Factory(cacheManager, okHttpClient))
            }
            .okHttpClient(okHttpClient)
            .allowHardware(false)
            .build()
    }
}
