package com.crocdb.friends

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class principale
 * Annotata con @HiltAndroidApp per abilitare Hilt dependency injection
 */
@HiltAndroidApp
class CrocdbApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        
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
