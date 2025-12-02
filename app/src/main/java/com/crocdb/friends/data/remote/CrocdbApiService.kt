package com.crocdb.friends.data.remote

import com.crocdb.friends.data.model.ApiResponse
import com.crocdb.friends.data.model.Platform
import com.crocdb.friends.data.model.Region
import com.crocdb.friends.data.model.SearchResults
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * CrocDB API Service
 * Base URL: https://api.crocdb.net
 */
interface CrocdbApiService {

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
        @Query("platforms") platforms: String? = null, // JSON array string
        @Query("regions") regions: String? = null, // JSON array string
        @Query("max_results") maxResults: Int = 50,
        @Query("page") page: Int = 1
    ): Response<ApiResponse<SearchResults>>

    /**
     * Ottieni lista piattaforme disponibili
     * Endpoint: GET /platforms
     */
    @GET("platforms")
    suspend fun getPlatforms(): Response<ApiResponse<List<Platform>>>

    /**
     * Ottieni lista regioni disponibili
     * Endpoint: GET /regions
     */
    @GET("regions")
    suspend fun getRegions(): Response<ApiResponse<List<Region>>>
}

/**
 * Request body per la ricerca
 */
data class SearchRequestBody(
    val search_key: String? = null,
    val platforms: List<String>? = null,
    val regions: List<String>? = null,
    val max_results: Int = 50,
    val page: Int = 1
)
