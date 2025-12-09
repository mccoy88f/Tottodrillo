package com.tottodrillo.data.remote

import com.tottodrillo.data.model.ApiResponse
import com.tottodrillo.data.model.DatabaseInfo
import com.tottodrillo.data.model.EntryResponse
import com.tottodrillo.data.model.PlatformsResponse
import com.tottodrillo.data.model.RegionsResponse
import com.tottodrillo.data.model.SearchResults
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API Service
 * Base URL: https://api.crocdb.net
 */
interface ApiService {

    /**
     * Cerca ROM nel database
     * Endpoint: POST /search
     */
    @POST("search")
    suspend fun searchRoms(
        @Body request: SearchRequestBody
    ): Response<ApiResponse<SearchResults>>

    /**
     * Cerca ROM tramite GET (alternativa)
     */
    @GET("search")
    suspend fun searchRomsGet(
        @Query("search_key") searchKey: String? = null,
        @Query("platforms") platforms: String? = null, // JSON array string (non documentato)
        @Query("regions") regions: String? = null, // JSON array string (non documentato)
        @Query("max_results") maxResults: Int = 50,
        @Query("page") page: Int = 1
    ): Response<ApiResponse<SearchResults>>

    /**
     * Ottieni una entry specifica per slug
     * Endpoint: POST /entry
     */
    @POST("entry")
    suspend fun getEntry(
        @Body request: EntryRequestBody
    ): Response<ApiResponse<EntryResponse>>

    /**
     * Ottieni una entry casuale
     * Endpoint: POST /entry/random
     */
    @POST("entry/random")
    suspend fun getRandomEntryPost(): Response<ApiResponse<EntryResponse>>

    /**
     * Ottieni una entry casuale
     * Endpoint: GET /entry/random
     */
    @GET("entry/random")
    suspend fun getRandomEntryGet(): Response<ApiResponse<EntryResponse>>

    /**
     * Ottieni lista piattaforme disponibili
     * Endpoint: GET /platforms
     */
    @GET("platforms")
    suspend fun getPlatforms(): Response<ApiResponse<PlatformsResponse>>

    /**
     * Ottieni lista regioni disponibili
     * Endpoint: GET /regions
     */
    @GET("regions")
    suspend fun getRegions(): Response<ApiResponse<RegionsResponse>>

    /**
     * Ottieni informazioni sul database
     * Endpoint: GET /info
     */
    @GET("info")
    suspend fun getDatabaseInfo(): Response<ApiResponse<DatabaseInfo>>
}

/**
 * Request body per la ricerca
 */
data class SearchRequestBody(
    val search_key: String? = null,
    val platforms: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val rom_id: String? = null,
    val max_results: Int = 50,
    val page: Int = 1
)

/**
 * Request body per /entry
 */
data class EntryRequestBody(
    val slug: String
)
