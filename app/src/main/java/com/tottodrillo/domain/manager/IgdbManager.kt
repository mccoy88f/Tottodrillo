package com.tottodrillo.domain.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.tottodrillo.data.repository.DownloadConfigRepository
import kotlinx.coroutines.flow.first
import com.tottodrillo.domain.model.IgdbSearchResult
import com.tottodrillo.domain.model.IgdbPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager per gestire le chiamate all'API IGDB
 */
@Singleton
class IgdbManager @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val configRepository: DownloadConfigRepository
) {
    companion object {
        private const val TWITCH_TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        private const val IGDB_API_URL = "https://api.igdb.com/v4"
        private const val PREFS_NAME = "igdb_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val TOKEN_CACHE_DURATION_MS = 50L * 24 * 60 * 60 * 1000 // 50 giorni (i token durano 60 giorni)
        private const val KEY_LAST_CLIENT_ID = "last_client_id"
        private const val KEY_LAST_CLIENT_SECRET = "last_client_secret"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Ottiene un token di accesso valido (dalla cache o richiedendolo)
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val config = configRepository.downloadConfig.first()
            if (!config.igdbEnabled) {
                android.util.Log.w("IgdbManager", "IGDB disabilitato nelle impostazioni")
                return@withContext null
            }
            val clientId = config.igdbClientId
            val clientSecret = config.igdbClientSecret
            if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                android.util.Log.w("IgdbManager", "Client ID o Secret IGDB mancanti")
                return@withContext null
            }

            // Se le credenziali sono cambiate, invalida il token cache
            val lastClientId = prefs.getString(KEY_LAST_CLIENT_ID, null)
            val lastClientSecret = prefs.getString(KEY_LAST_CLIENT_SECRET, null)
            if (lastClientId != clientId || lastClientSecret != clientSecret) {
                android.util.Log.d("IgdbManager", "Credenziali IGDB cambiate, invalido token cache")
                prefs.edit()
                    .remove(KEY_ACCESS_TOKEN)
                    .remove(KEY_TOKEN_EXPIRES_AT)
                    .putString(KEY_LAST_CLIENT_ID, clientId)
                    .putString(KEY_LAST_CLIENT_SECRET, clientSecret)
                    .apply()
            }

            // Controlla se c'è un token in cache ancora valido
            val cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0)
            
            if (cachedToken != null && expiresAt > System.currentTimeMillis()) {
                android.util.Log.d("IgdbManager", "Usando token IGDB dalla cache")
                return@withContext cachedToken
            }
            
            // Richiedi un nuovo token
            android.util.Log.d("IgdbManager", "Richiesta nuovo token IGDB")
            val request = Request.Builder()
                .url("$TWITCH_TOKEN_URL?client_id=$clientId&client_secret=$clientSecret&grant_type=client_credentials")
                .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e("IgdbManager", "Errore richiesta token: ${response.code}")
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                android.util.Log.e("IgdbManager", "Response body null")
                return@withContext null
            }
            
            val json = JsonParser.parseString(responseBody).asJsonObject
            val accessToken = json.get("access_token")?.asString
            val expiresIn = json.get("expires_in")?.asInt ?: 5184000 // Default 60 giorni in secondi
            
            if (accessToken == null) {
                android.util.Log.e("IgdbManager", "Access token null nella risposta")
                return@withContext null
            }
            
            // Salva in cache
            val expiresAtTime = System.currentTimeMillis() + (expiresIn * 1000L)
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_TOKEN_EXPIRES_AT, expiresAtTime)
                .apply()
            
            android.util.Log.d("IgdbManager", "Token IGDB ottenuto e salvato in cache")
            accessToken
        } catch (e: Exception) {
            android.util.Log.e("IgdbManager", "Errore ottenimento token IGDB", e)
            null
        }
    }
    
    /**
     * Cerca giochi su IGDB
     * @param query Query di ricerca (nome del gioco)
     * @param platformFilter Filtro piattaforma opzionale (nome piattaforma IGDB)
     * @return Lista di risultati di ricerca
     */
    suspend fun searchGames(
        query: String,
        platformFilters: List<String> = emptyList()
    ): List<IgdbSearchResult> = withContext(Dispatchers.IO) {
        try {
            val config = configRepository.downloadConfig.first()
            val clientId = config.igdbClientId
            if (!config.igdbEnabled || clientId.isNullOrBlank()) {
                android.util.Log.w("IgdbManager", "IGDB disabilitato o credenziali mancanti")
                return@withContext emptyList()
            }
            val accessToken = getAccessToken()
            if (accessToken == null) {
                android.util.Log.e("IgdbManager", "Impossibile ottenere token di accesso")
                return@withContext emptyList()
            }
            
            // Prendi il primo filtro piattaforma disponibile
            val platformFilter = platformFilters.firstOrNull()

            // Costruisci la query Apicalypse
            val apicalypseQuery = buildString {
                append("search \"$query\";")
                append("fields name, summary, storyline, genres.name, platforms.name, first_release_date,")
                append("cover.image_id, screenshots.image_id, involved_companies.company.name,")
                append("involved_companies.developer, involved_companies.publisher,")
                append("total_rating, url;")
                if (platformFilter != null) {
                    // Match case-insensitive su name
                    append("where platforms.name ~ \"(?i).*${platformFilter.replace("\"", "\\\"") }.*\";")
                }
                append("limit 5;")
            }
            
            val request = Request.Builder()
                .url("$IGDB_API_URL/games")
                .header("Client-ID", clientId)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .header("Content-Type", "text/plain")
                .post(apicalypseQuery.toRequestBody("text/plain".toMediaType()))
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e("IgdbManager", "Errore ricerca IGDB: ${response.code}")
                return@withContext emptyList()
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null || responseBody == "[]") {
                return@withContext emptyList()
            }
            
            // Parse JSON array
            val jsonArray = JsonParser.parseString(responseBody).asJsonArray
            val results = mutableListOf<IgdbSearchResult>()
            
            for (element in jsonArray) {
                val game = element.asJsonObject
                val igdbId = game.get("id")?.asLong ?: continue
                val name = game.get("name")?.asString ?: continue
                
                // Summary e storyline
                val summary = game.get("summary")?.asString
                val storyline = game.get("storyline")?.asString
                
                // Data di rilascio
                val firstReleaseDate = game.get("first_release_date")?.asLong
                
                // Cover
                val coverImageId = game.get("cover")?.asJsonObject?.get("image_id")?.asString
                
                // Screenshots (max 3)
                val screenshots = mutableListOf<String>()
                game.get("screenshots")?.asJsonArray?.take(3)?.forEach { screenshot: JsonElement ->
                    screenshot.asJsonObject.get("image_id")?.asString?.let { imageId ->
                        screenshots.add("https://images.igdb.com/igdb/image/upload/t_screenshot_huge/$imageId.jpg")
                    }
                }
                
                // Piattaforme
                val platforms = mutableListOf<IgdbPlatform>()
                game.get("platforms")?.asJsonArray?.forEach { platform: JsonElement ->
                    val platformObj = platform.asJsonObject
                    val platformId = platformObj.get("id")?.asInt
                    val platformName = platformObj.get("name")?.asString
                    if (platformId != null && platformName != null) {
                        platforms.add(IgdbPlatform(platformId, platformName, null))
                    }
                }
                
                // Generi
                val genres = mutableListOf<String>()
                game.get("genres")?.asJsonArray?.forEach { genre: JsonElement ->
                    genre.asJsonObject.get("name")?.asString?.let { genres.add(it) }
                }
                
                // Developer e Publisher
                val developers = mutableListOf<String>()
                val publishers = mutableListOf<String>()
                game.get("involved_companies")?.asJsonArray?.forEach { company: JsonElement ->
                    val companyObj = company.asJsonObject
                    val companyName = companyObj.get("company")?.asJsonObject?.get("name")?.asString
                    val isDeveloper = companyObj.get("developer")?.asBoolean ?: false
                    val isPublisher = companyObj.get("publisher")?.asBoolean ?: false
                    
                    if (companyName != null) {
                        if (isDeveloper) developers.add(companyName)
                        if (isPublisher) publishers.add(companyName)
                    }
                }
                
                // Rating
                val rating = game.get("total_rating")?.asDouble
                
                // URL
                val url = game.get("url")?.asString
                
                // Cover URL (se disponibile)
                val coverUrl = if (coverImageId != null) {
                    "https://images.igdb.com/igdb/image/upload/t_cover_big/$coverImageId.jpg"
                } else null
                
                results.add(
                    IgdbSearchResult(
                        igdbId = igdbId,
                        name = name,
                        summary = summary,
                        storyline = storyline,
                        firstReleaseDate = firstReleaseDate,
                        coverImageId = coverImageId,
                        screenshots = screenshots,
                        platforms = platforms,
                        genres = genres,
                        developers = developers,
                        publishers = publishers,
                        rating = rating,
                        url = url
                    )
                )
            }
            
            results
        } catch (e: Exception) {
            android.util.Log.e("IgdbManager", "Errore ricerca IGDB", e)
            emptyList()
        }
    }
}

