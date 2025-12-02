package com.crocdb.friends.data.model

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
    @SerializedName("total")
    val total: Int? = null,
    @SerializedName("page")
    val page: Int? = null,
    @SerializedName("max_results")
    val maxResults: Int? = null
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
    @SerializedName("size")
    val size: String? = null
)

/**
 * Piattaforma
 */
data class Platform(
    @SerializedName("code")
    val code: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("manufacturer")
    val manufacturer: String? = null,
    @SerializedName("generation")
    val generation: Int? = null
)

/**
 * Regione
 */
data class Region(
    @SerializedName("code")
    val code: String,
    @SerializedName("name")
    val name: String
)

/**
 * Richiesta di ricerca
 */
data class SearchRequest(
    val searchKey: String? = null,
    val platforms: List<String>? = null,
    val regions: List<String>? = null,
    val maxResults: Int = 50,
    val page: Int = 1
)
