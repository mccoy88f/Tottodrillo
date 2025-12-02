package com.crocdb.friends

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class principale
 * Annotata con @HiltAndroidApp per abilitare Hilt dependency injection
 */
@HiltAndroidApp
class CrocdbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Inizializzazione app
        // Qui possiamo aggiungere configurazioni come:
        // - Analytics
        // - Crash reporting
        // - WorkManager
        // - Database Room
    }
}
