package com.tottodrillo.data.remote

import com.google.gson.Gson
import com.tottodrillo.data.model.EntryResponse
import com.tottodrillo.data.model.RegionsResponse
import com.tottodrillo.data.model.SearchResults
import com.tottodrillo.domain.model.SourceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Executor per sorgenti Python
 * Esegue script Python usando Chaquopy o un interprete Python esterno
 * 
 * NOTA: Richiede Chaquopy per funzionare. Aggiungi al build.gradle.kts:
 * 
 * plugins {
 *     id("com.chaquo.python") version "x.x.x"
 * }
 * 
 * android {
 *     defaultConfig {
 *         ndk {
 *             abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
 *         }
 *     }
 * }
 */
class PythonSourceExecutor(
    private val metadata: SourceMetadata,
    private val sourceDir: File,
    private val gson: Gson
) : SourceExecutor {
    
    private val pythonScript = metadata.pythonScript
        ?: throw IllegalArgumentException("pythonScript è richiesto per sorgenti Python")
    
    private val scriptFile = File(sourceDir, pythonScript)
    
    init {
        if (!scriptFile.exists()) {
            throw IllegalStateException("Script Python non trovato: ${scriptFile.absolutePath}")
        }
    }
    
    override suspend fun searchRoms(
        searchKey: String?,
        platforms: List<String>,
        regions: List<String>,
        maxResults: Int,
        page: Int
    ): Result<SearchResults> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf(
                "method" to "searchRoms",
                "search_key" to searchKey,
                "platforms" to platforms,
                "regions" to regions,
                "max_results" to maxResults,
                "page" to page
            )
            
            val jsonResult = executePythonScript(params)
            android.util.Log.d("PythonSourceExecutor", "📦 Risultato JSON Python (primi 500 caratteri): ${jsonResult.take(500)}")
            val searchResults = gson.fromJson(jsonResult, SearchResults::class.java)
            // Verifica se le ROM hanno immagini
            val romsWithImages = searchResults.results.filterNotNull().filter { it.boxartUrl != null || !it.boxartUrls.isNullOrEmpty() }
            android.util.Log.d("PythonSourceExecutor", "🖼️ ROM con immagini: ${romsWithImages.size}/${searchResults.results.size}")
            if (romsWithImages.isNotEmpty()) {
                android.util.Log.d("PythonSourceExecutor", "🖼️ Esempio ROM con immagine: ${romsWithImages.first().title} -> ${romsWithImages.first().boxartUrl}")
            }
            Result.success(searchResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getEntry(slug: String, includeDownloadLinks: Boolean): Result<EntryResponse> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf(
                "method" to "getEntry",
                "slug" to slug,
                "include_download_links" to includeDownloadLinks
            )
            
            val jsonResult = executePythonScript(params)
            val entryResponse = gson.fromJson(jsonResult, EntryResponse::class.java)
            Result.success(entryResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getPlatforms(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf("method" to "getPlatforms")
            val jsonResult = executePythonScript(params)
            @Suppress("UNCHECKED_CAST")
            val platforms = gson.fromJson(jsonResult, Map::class.java) as Map<String, Any>
            Result.success(platforms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getRegions(): Result<RegionsResponse> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf("method" to "getRegions")
            val jsonResult = executePythonScript(params)
            val regionsResponse = gson.fromJson(jsonResult, RegionsResponse::class.java)
            Result.success(regionsResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Esegue lo script Python con i parametri dati
     * Restituisce JSON come stringa
     */
    private suspend fun executePythonScript(params: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            // Usa Chaquopy se disponibile
            val python = try {
                if (!com.chaquo.python.Python.isStarted()) {
                    throw IllegalStateException("Python non è stato inizializzato. Assicurati che TottodrilloApp inizializzi Chaquopy.")
                }
                com.chaquo.python.Python.getInstance()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Chaquopy non è disponibile. Aggiungi il plugin Chaquopy al build.gradle.kts e inizializzalo in TottodrilloApp",
                    e
                )
            }
            
            // Aggiungi la directory della sorgente al sys.path di Python
            val sysModule = python.getModule("sys")
            val sourceDirPath = sourceDir.absolutePath
            val pathList = sysModule["path"] as com.chaquo.python.PyObject
            pathList.callAttr("insert", 0, sourceDirPath)
            
            // Carica il modulo Python (il nome del modulo è il nome del file senza estensione)
            val moduleName = pythonScript.removeSuffix(".py")
            val module = python.getModule(moduleName)
            
            // Passa anche il path della source directory per permettere al Python di leggere platform_mapping.json
            val paramsWithSourceDir = params.toMutableMap()
            paramsWithSourceDir["source_dir"] = sourceDir.absolutePath
            
            // Chiama la funzione execute con i parametri JSON
            val paramsJson = gson.toJson(paramsWithSourceDir)
            val result = module.callAttr("execute", paramsJson)
            
            result.toString()
        } catch (e: Exception) {
            throw RuntimeException("Errore nell'esecuzione script Python: ${e.message}", e)
        }
    }
    
    companion object {
        /**
         * Crea un PythonSourceExecutor
         */
        fun create(metadata: SourceMetadata, sourceDir: File, gson: Gson): PythonSourceExecutor {
            return PythonSourceExecutor(metadata, sourceDir, gson)
        }
    }
}

