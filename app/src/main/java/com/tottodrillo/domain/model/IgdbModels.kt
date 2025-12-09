package com.tottodrillo.domain.model

/**
 * Risultato di ricerca IGDB per una ROM
 */
data class IgdbSearchResult(
    val igdbId: Long,
    val name: String,
    val summary: String?,
    val storyline: String?,
    val firstReleaseDate: Long?, // Unix timestamp
    val coverImageId: String?,
    val screenshots: List<String>, // Max 3
    val platforms: List<IgdbPlatform>,
    val genres: List<String>,
    val developers: List<String>,
    val publishers: List<String>,
    val rating: Double?, // 0-100
    val url: String?
)

/**
 * Piattaforma IGDB
 */
data class IgdbPlatform(
    val igdbId: Int,
    val name: String,
    val abbreviation: String?
)

/**
 * Dati per il dialog di conferma importazione IGDB
 */
data class IgdbImportConfirmation(
    val romTitle: String, // Titolo ROM corrente
    val igdbTitle: String, // Titolo IGDB
    val romPlatform: String, // Piattaforma ROM corrente
    val igdbPlatforms: List<String>, // Piattaforme IGDB
    val romRegions: List<String>, // Regioni ROM corrente
    val igdbRegions: List<String>? = null // Regioni IGDB (se disponibili)
)

