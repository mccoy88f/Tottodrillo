package com.tottodrillo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Risposta standard API CrocDB
 */
data class ApiResponse<T>(
    @SerializedName("info")
    val info: ApiInfo,
    @SerializedName("data")
    val data: T
)

/**
 * Informazioni sulla risposta API
 */
data class ApiInfo(
    @SerializedName("error")
    val error: String? = null,
    @SerializedName("message")
    val message: String? = null
)

/**
 * Risultati della ricerca ROM
 */
data class SearchResults(
    @SerializedName("results")
    val results: List<RomEntry>,
    @SerializedName("current_results")
    val currentResults: Int? = null,
    @SerializedName("total_results")
    val totalResults: Int? = null,
    @SerializedName("current_page")
    val currentPage: Int? = null,
    @SerializedName("total_pages")
    val totalPages: Int? = null
)

/**
 * Entry di una ROM
 */
data class RomEntry(
    @SerializedName("slug")
    val slug: String,
    @SerializedName("rom_id")
    val romId: String?,
    @SerializedName("title")
    val title: String,
    @SerializedName("platform")
    val platform: String,
    @SerializedName("boxart_url")
    val boxartUrl: String?,
    @SerializedName("boxart_urls")
    val boxartUrls: List<String>? = null, // Lista di tutte le immagini (box + screen) - DEPRECATO
    @SerializedName("box_image")
    val boxImage: String? = null, // Immagine box art (obbligatoria)
    @SerializedName("screen_image")
    val screenImage: String? = null, // Immagine screen (facoltativa)
    @SerializedName("regions")
    val regions: List<String>,
    @SerializedName("links")
    val links: List<RomLink>
)

/**
 * Link per il download di una ROM
 */
data class RomLink(
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("format")
    val format: String,
    @SerializedName("url")
    val url: String,
    @SerializedName("filename")
    val filename: String? = null,
    @SerializedName("host")
    val host: String? = null,
    @SerializedName("size")
    val size: Long? = null,
    @SerializedName("size_str")
    val sizeStr: String? = null,
    @SerializedName("source_url")
    val sourceUrl: String? = null,
    @SerializedName("requires_webview")
    val requiresWebView: Boolean = false // Se true, l'URL richiede un WebView per gestire JavaScript/countdown
)

/**
 * Risposta piattaforme dall'API
 */
data class PlatformsResponse(
    @SerializedName("platforms")
    val platforms: Map<String, PlatformData>
)

/**
 * Dati piattaforma dall'API
 */
data class PlatformData(
    @SerializedName("brand")
    val brand: String,
    @SerializedName("name")
    val name: String
)

/**
 * Piattaforma
 */
data class Platform(
    val code: String,
    val name: String,
    val manufacturer: String
)

/**
 * Risposta regioni dall'API
 */
data class RegionsResponse(
    @SerializedName("regions")
    val regions: Map<String, String>
)

/**
 * Regione
 */
data class Region(
    val code: String,
    val name: String
)

/**
 * Richiesta di ricerca
 */
data class SearchRequest(
    val searchKey: String? = null,
    val platforms: List<String>? = null,
    val regions: List<String>? = null,
    val romId: String? = null,
    val maxResults: Int = 100,
    val page: Int = 1
)

/**
 * Risposta dettaglio entry (endpoint /entry e /entry/random)
 */
data class EntryResponse(
    @SerializedName("entry")
    val entry: RomEntry? = null  // Nullable perché alcune sorgenti possono non trovare la ROM
)

/**
 * Info generali sul database (endpoint /info)
 */
data class DatabaseInfo(
    @SerializedName("total_entries")
    val totalEntries: Int
)
