package com.tottodrillo.data.remote

import com.tottodrillo.data.model.EntryResponse
import com.tottodrillo.data.model.RegionsResponse
import com.tottodrillo.data.model.SearchResults
import com.tottodrillo.domain.model.SourceMetadata
import java.io.File

/**
 * Interfaccia comune per tutti i tipi di sorgenti (API, Java, Python)
 * Ogni sorgente deve implementare questa interfaccia per esporre i dati
 * nel formato standard di Tottodrillo
 */
interface SourceExecutor {
    /**
     * Cerca ROM nella sorgente
     */
    suspend fun searchRoms(
        searchKey: String? = null,
        platforms: List<String> = emptyList(),
        regions: List<String> = emptyList(),
        maxResults: Int = 50,
        page: Int = 1
    ): Result<SearchResults>
    
    /**
     * Ottiene una entry specifica per slug
     * @param includeDownloadLinks Se false, i download links non vengono estratti (utile per home screen e ricerca)
     */
    suspend fun getEntry(slug: String, includeDownloadLinks: Boolean = true): Result<EntryResponse>
    
    /**
     * Ottiene le piattaforme disponibili
     */
    suspend fun getPlatforms(): Result<Map<String, Any>>
    
    /**
     * Ottiene le regioni disponibili
     */
    suspend fun getRegions(): Result<RegionsResponse>
    
    companion object {
        /**
         * Crea un SourceExecutor appropriato basato sul tipo di sorgente
         */
        fun create(
            metadata: SourceMetadata,
            sourceDir: File,
            okHttpClient: okhttp3.OkHttpClient? = null,
            gson: com.google.gson.Gson? = null
        ): SourceExecutor {
            // Retrocompatibilità: se type è null o vuoto, assume "api" (comportamento predefinito)
            val type = metadata.type.takeIf { !it.isNullOrBlank() } ?: "api"
            
            val sourceType = when (type.lowercase()) {
                "api" -> SourceType.API
                "java", "kotlin" -> SourceType.JAVA
                "python" -> SourceType.PYTHON
                else -> throw IllegalArgumentException("Tipo sorgente non supportato: $type")
            }
            
            return when (sourceType) {
                SourceType.API -> {
                    require(okHttpClient != null && gson != null) {
                        "OkHttpClient e Gson sono richiesti per sorgenti API"
                    }
                    SourceApiAdapter.create(metadata, sourceDir, okHttpClient, gson)
                }
                SourceType.JAVA -> {
                    JavaSourceExecutor.create(metadata, sourceDir)
                }
                SourceType.PYTHON -> {
                    require(gson != null) { "Gson è richiesto per sorgenti Python" }
                    PythonSourceExecutor.create(metadata, sourceDir, gson)
                }
            }
        }
    }
}

/**
 * Tipo di sorgente
 */
enum class SourceType {
    API,    // Chiamate HTTP API
    JAVA,   // Codice Java/Kotlin eseguito localmente
    PYTHON  // Script Python eseguito localmente
}

