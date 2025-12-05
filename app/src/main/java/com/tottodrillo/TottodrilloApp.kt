package com.tottodrillo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.HiltAndroidApp

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
     * Configura ImageLoader globale per Coil con supporto SVG
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
