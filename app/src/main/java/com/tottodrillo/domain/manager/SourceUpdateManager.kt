package com.tottodrillo.domain.manager

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tottodrillo.domain.model.Source
import com.tottodrillo.domain.model.SourceUpdate
import com.tottodrillo.domain.model.SourceVersionInfo
import com.tottodrillo.domain.model.SourcesVersionsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager per verificare e gestire aggiornamenti delle sorgenti
 */
@Singleton
class SourceUpdateManager @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val sourceManager: SourceManager
) {
    companion object {
        private const val SOURCES_VERSIONS_URL = "https://raw.githubusercontent.com/mccoy88f/Tottodrillo-Source/refs/heads/main/sources-versions.json"
        private const val CACHE_FILE = "sources_versions_cache.json"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 ore
    }

    /**
     * Ottiene la versione corrente dell'app
     */
    private fun getCurrentAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            packageInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Errore ottenimento versione app", e)
            "0.0.0"
        }
    }

    /**
     * Carica le versioni disponibili dal repository GitHub
     */
    suspend fun fetchAvailableVersions(): Result<SourcesVersionsResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(SOURCES_VERSIONS_URL)
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                android.util.Log.e("SourceUpdateManager", "Errore richiesta versioni: ${response.code}")
                return@withContext Result.failure(
                    Exception("Errore nel caricamento versioni: ${response.code}")
                )
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                return@withContext Result.failure(Exception("Response body null"))
            }

            val versionsResponse: SourcesVersionsResponse = gson.fromJson(
                responseBody,
                SourcesVersionsResponse::class.java
            )

            // Salva in cache
            saveToCache(versionsResponse)

            Result.success(versionsResponse)
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Errore nel fetch versioni", e)
            
            // Prova a caricare dalla cache
            loadFromCache()?.let { cached ->
                android.util.Log.d("SourceUpdateManager", "Usando versione in cache")
                return@withContext Result.success(cached)
            }
            
            Result.failure(e)
        }
    }

    /**
     * Verifica se ci sono aggiornamenti disponibili per le sorgenti installate
     */
    suspend fun checkForUpdates(installedSources: List<Source>): List<SourceUpdate> = withContext(Dispatchers.IO) {
        try {
            val versionsResult = fetchAvailableVersions()
            if (versionsResult.isFailure) {
                android.util.Log.e("SourceUpdateManager", "Impossibile caricare versioni disponibili")
                return@withContext emptyList()
            }

            val availableVersions = versionsResult.getOrNull()?.sources ?: emptyList()
            val currentAppVersion = getCurrentAppVersion()
            val updates = mutableListOf<SourceUpdate>()

            for (installedSource in installedSources) {
                val availableVersion = availableVersions.find { it.id == installedSource.id }
                if (availableVersion != null) {
                    // Verifica compatibilità con versione app
                    if (availableVersion.minAppVersion != null) {
                        if (!isVersionNewerOrEqual(currentAppVersion, availableVersion.minAppVersion)) {
                            android.util.Log.d(
                                "SourceUpdateManager",
                                "Sorgente ${installedSource.id} richiede app versione ${availableVersion.minAppVersion}, attuale: $currentAppVersion"
                            )
                            continue
                        }
                    }

                    // Verifica se c'è un aggiornamento disponibile
                    if (sourceManager.isVersionNewer(availableVersion.version, installedSource.version)) {
                        updates.add(
                            SourceUpdate(
                                sourceId = installedSource.id,
                                sourceName = installedSource.name,
                                currentVersion = installedSource.version,
                                availableVersion = availableVersion.version,
                                downloadUrl = availableVersion.downloadUrl,
                                changelog = availableVersion.changelog,
                                minAppVersion = availableVersion.minAppVersion
                            )
                        )
                    }
                }
            }

            updates
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Errore verifica aggiornamenti", e)
            emptyList()
        }
    }

    /**
     * Verifica se una versione è più recente o uguale a un'altra
     */
    private fun isVersionNewerOrEqual(version1: String, version2: String): Boolean {
        if (version1 == version2) return true
        return sourceManager.isVersionNewer(version1, version2)
    }

    /**
     * Salva la risposta in cache
     */
    private suspend fun saveToCache(versionsResponse: SourcesVersionsResponse) = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE)
            val json = gson.toJson(versionsResponse)
            cacheFile.writeText(json)
            android.util.Log.d("SourceUpdateManager", "Cache salvata")
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Errore salvataggio cache", e)
        }
    }
    
    /**
     * Invalida la cache delle versioni disponibili
     * Utile dopo un aggiornamento per forzare il refresh
     */
    fun invalidateCache() {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE)
            if (cacheFile.exists()) {
                cacheFile.delete()
                android.util.Log.d("SourceUpdateManager", "Cache invalidata")
            }
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Errore invalidazione cache", e)
        }
    }

    /**
     * Carica la risposta dalla cache se valida
     */
    private suspend fun loadFromCache(): SourcesVersionsResponse? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE)
            if (!cacheFile.exists()) {
                return@withContext null
            }

            // Verifica se la cache è ancora valida
            val lastModified = cacheFile.lastModified()
            val now = System.currentTimeMillis()
            if (now - lastModified > CACHE_DURATION_MS) {
                android.util.Log.d("SourceUpdateManager", "Cache scaduta")
                return@withContext null
            }

            val json = cacheFile.readText()
            gson.fromJson(json, SourcesVersionsResponse::class.java)
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Errore caricamento cache", e)
            null
        }
    }
}

