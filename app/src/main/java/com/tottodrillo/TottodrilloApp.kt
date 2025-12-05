package com.tottodrillo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.network.okhttp.OkHttpNetworkFetcher
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Application class principale
 * Annotata con @HiltAndroidApp per abilitare Hilt dependency injection
 */
@HiltAndroidApp
class TottodrilloApp : Application(), ImageLoaderFactory {

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
        // CookieJar in memoria per gestire i cookie di sessione per le immagini
        val cookieJar = object : CookieJar {
            private val cookies = mutableListOf<Cookie>()
            private val visitedRomPages = mutableSetOf<String>() // Cache per ROM già visitate
            
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
                        val romPageUrl = HttpUrl.parse("https://vimm.net/vault/$romId")
                        if (romPageUrl != null) {
                            try {
                                // Usa un client temporaneo senza CookieJar per visitare la pagina ROM
                                val tempClient = OkHttpClient.Builder()
                                    .cookieJar(this) // Usa lo stesso CookieJar per salvare i cookie
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
                            }
                        }
                    }
                }
                return cookies.filter { it.matches(url) }
            }
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
        
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .networkFetcher(OkHttpNetworkFetcher(okHttpClient))
            .build()
    }
}
