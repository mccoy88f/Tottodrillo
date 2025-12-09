package com.tottodrillo.data.remote

import com.google.gson.Gson
import com.tottodrillo.data.model.SourceApiConfig
import com.tottodrillo.data.model.EndpointConfig
import com.tottodrillo.domain.model.SourceMetadata
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client API generico per sorgenti installabili
 * Esegue chiamate API basate su configurazione JSON invece di interfacce Retrofit
 */
class SourceApiClient(
    private val metadata: SourceMetadata,
    private val apiConfig: SourceApiConfig,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    
    /**
     * Esegue una chiamata API generica
     */
    suspend fun call(
        endpointName: String,
        queryParams: Map<String, Any?> = emptyMap(),
        body: Any? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = apiConfig.endpoints[endpointName]
            ?: return@withContext Result.failure(IllegalArgumentException("Endpoint '$endpointName' non trovato"))
        
        return@withContext try {
            val url = buildUrl(endpoint, queryParams)
            val request = buildRequest(endpoint, url, body)
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IllegalStateException("Response body vuoto"))
                Result.success(responseBody)
            } else {
                Result.failure(
                    IllegalStateException("API call failed: ${response.code} ${response.message}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Costruisce l'URL completo per l'endpoint
     */
    private fun buildUrl(endpoint: EndpointConfig, queryParams: Map<String, Any?>): String {
        val baseUrl = apiConfig.baseUrl.trimEnd('/')
        val path = endpoint.path.trimStart('/')
        var url = "$baseUrl/$path"
        
        // Aggiungi query parameters
        if (endpoint.queryParams != null && queryParams.isNotEmpty()) {
            val queryString = queryParams.entries
                .filter { it.value != null }
                .joinToString("&") { "${it.key}=${it.value}" }
            
            if (queryString.isNotEmpty()) {
                url += "?$queryString"
            }
        }
        
        return url
    }
    
    /**
     * Costruisce la Request HTTP
     */
    private fun buildRequest(
        endpoint: EndpointConfig,
        url: String,
        body: Any?
    ): Request {
        val builder = Request.Builder().url(url)
        
        when (endpoint.method.uppercase()) {
            "GET" -> {
                // GET non ha body
            }
            "POST", "PUT", "PATCH" -> {
                if (body != null) {
                    val jsonBody = gson.toJson(body)
                    val mediaType = "application/json".toMediaType()
                    builder.method(
                        endpoint.method,
                        jsonBody.toRequestBody(mediaType)
                    )
                } else if (endpoint.bodyModel != null) {
                    // Body richiesto ma non fornito
                    throw IllegalArgumentException("Body richiesto per endpoint ${endpoint.path}")
                }
            }
            else -> {
                throw IllegalArgumentException("Metodo HTTP non supportato: ${endpoint.method}")
            }
        }
        
        return builder.build()
    }
    
    companion object {
        /**
         * Crea un SourceApiClient da una sorgente installata
         */
        fun create(
            metadata: SourceMetadata,
            sourceDir: File,
            okHttpClient: OkHttpClient,
            gson: Gson
        ): SourceApiClient {
            val apiConfigFile = File(sourceDir, "api_config.json")
            if (!apiConfigFile.exists()) {
                throw IllegalStateException("api_config.json non trovato per sorgente ${metadata.id}")
            }
            
            val apiConfig = gson.fromJson(
                apiConfigFile.readText(),
                SourceApiConfig::class.java
            )
            
            return SourceApiClient(metadata, apiConfig, okHttpClient, gson)
        }
    }
}

