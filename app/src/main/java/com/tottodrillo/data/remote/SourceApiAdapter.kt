package com.tottodrillo.data.remote

import com.google.gson.Gson
import com.tottodrillo.data.model.ApiResponse
import com.tottodrillo.data.model.EntryResponse
import com.tottodrillo.data.model.RegionsResponse
import com.tottodrillo.data.model.SearchResults
import com.tottodrillo.data.remote.SearchRequestBody
import com.tottodrillo.data.remote.EntryRequestBody
import com.tottodrillo.domain.model.SourceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Adapter che converte chiamate API generiche in chiamate specifiche per ogni sorgente
 * Gestisce la conversione tra SourceApiClient e i modelli di dominio
 * 
 * Implementa SourceExecutor per sorgenti di tipo API
 */
class SourceApiAdapter(
    private val sourceClient: SourceApiClient,
    private val metadata: SourceMetadata,
    private val gson: Gson
) : SourceExecutor {
    
    /**
     * Cerca ROM in tutte le sorgenti
     */
    override suspend fun searchRoms(
        searchKey: String?,
        platforms: List<String>,
        regions: List<String>,
        maxResults: Int,
        page: Int
    ): Result<SearchResults> = withContext(Dispatchers.IO) {
        try {
            val requestBody = mapOf(
                "search_key" to searchKey,
                "platforms" to platforms,
                "regions" to regions,
                "max_results" to maxResults,
                "page" to page
            )
            
            val result = sourceClient.call("search", body = requestBody)
            
            result.fold(
                onSuccess = { json ->
                    // Parse la risposta JSON
                    val apiResponse = gson.fromJson(json, ApiResponse::class.java)
                    val searchResults = gson.fromJson(
                        gson.toJsonTree(apiResponse.data),
                        SearchResults::class.java
                    )
                    Result.success(searchResults)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Ottiene una entry specifica per slug
     */
    override suspend fun getEntry(slug: String, includeDownloadLinks: Boolean): Result<EntryResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = mapOf("slug" to slug)
            
            val result = sourceClient.call("get_entry", body = requestBody)
            
            result.fold(
                onSuccess = { json ->
                    val apiResponse = gson.fromJson(json, ApiResponse::class.java)
                    val entryResponse = gson.fromJson(
                        gson.toJsonTree(apiResponse.data),
                        EntryResponse::class.java
                    )
                    Result.success(entryResponse)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Ottiene le piattaforme disponibili
     */
    override suspend fun getPlatforms(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val result = sourceClient.call("get_platforms")
            
            result.fold(
                onSuccess = { json ->
                    val apiResponse = gson.fromJson(json, ApiResponse::class.java)
                    @Suppress("UNCHECKED_CAST")
                    val platforms = gson.fromJson(
                        gson.toJsonTree(apiResponse.data),
                        Map::class.java
                    ) as Map<String, Any>
                    Result.success(platforms)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Ottiene le regioni disponibili
     */
    override suspend fun getRegions(): Result<RegionsResponse> = withContext(Dispatchers.IO) {
        try {
            val result = sourceClient.call("get_regions")
            
            result.fold(
                onSuccess = { json ->
                    val apiResponse = gson.fromJson(json, ApiResponse::class.java)
                    val regionsResponse = gson.fromJson(
                        gson.toJsonTree(apiResponse.data),
                        RegionsResponse::class.java
                    )
                    Result.success(regionsResponse)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    companion object {
        /**
         * Crea un SourceApiAdapter da una sorgente installata
         */
        fun create(
            metadata: SourceMetadata,
            sourceDir: File,
            okHttpClient: okhttp3.OkHttpClient,
            gson: Gson
        ): SourceApiAdapter {
            val client = SourceApiClient.create(metadata, sourceDir, okHttpClient, gson)
            return SourceApiAdapter(client, metadata, gson)
        }
    }
}

