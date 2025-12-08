package com.tottodrillo.domain.model

/**
 * Modello UI per una ROM
 */
data class Rom(
    val slug: String,
    val id: String?,
    val title: String,
    val platform: PlatformInfo,
    val coverUrl: String?, // Immagine principale (prima che arriva)
    val coverUrls: List<String> = emptyList(), // Lista di tutte le immagini per il carosello
    val regions: List<RegionInfo>,
    val downloadLinks: List<DownloadLink>,
    val isFavorite: Boolean = false,
    val sourceId: String? = null, // ID della sorgente principale (prima che arriva)
    // Dati IGDB (opzionali, importati da IGDB)
    val igdbSummary: String? = null, // Summary/descrizione da IGDB
    val igdbStoryline: String? = null, // Storyline da IGDB
    val igdbYear: Int? = null, // Anno di rilascio da IGDB
    val igdbGenres: List<String> = emptyList(), // Generi da IGDB
    val igdbDeveloper: String? = null, // Developer da IGDB
    val igdbPublisher: String? = null, // Publisher da IGDB
    val igdbRating: Double? = null, // Rating da IGDB (0-100)
    val igdbScreenshots: List<String> = emptyList(), // Screenshots da IGDB (max 3)
    val igdbUrl: String? = null // URL IGDB del gioco
)

/**
 * Informazioni piattaforma per UI
 */
data class PlatformInfo(
    val code: String,
    val displayName: String,
    val manufacturer: String? = null,
    val imagePath: String? = null, // Percorso dell'immagine (es. "logos/3do.svg")
    val description: String? = null // Descrizione della piattaforma
) {
    companion object {
        val UNKNOWN = PlatformInfo("unknown", "Unknown", null, null, null)
    }
}

/**
 * Informazioni regione per UI
 */
data class RegionInfo(
    val code: String,
    val displayName: String,
    val flagEmoji: String
) {
    companion object {
        fun fromCode(code: String): RegionInfo = when (code.uppercase()) {
            "US" -> RegionInfo("US", "USA", "🇺🇸")
            "EU" -> RegionInfo("EU", "Europe", "🇪🇺")
            "JP" -> RegionInfo("JP", "Japan", "🇯🇵")
            "KR" -> RegionInfo("KR", "Korea", "🇰🇷")
            "CN" -> RegionInfo("CN", "China", "🇨🇳")
            "AU" -> RegionInfo("AU", "Australia", "🇦🇺")
            "BR" -> RegionInfo("BR", "Brazil", "🇧🇷")
            "UK" -> RegionInfo("UK", "UK", "🇬🇧")
            "FR" -> RegionInfo("FR", "France", "🇫🇷")
            "DE" -> RegionInfo("DE", "Germany", "🇩🇪")
            "IT" -> RegionInfo("IT", "Italy", "🇮🇹")
            "ES" -> RegionInfo("ES", "Spain", "🇪🇸")
            "NL" -> RegionInfo("NL", "Netherlands", "🇳🇱")
            "SE" -> RegionInfo("SE", "Sweden", "🇸🇪")
            "NO" -> RegionInfo("NO", "Norway", "🇳🇴")
            "DK" -> RegionInfo("DK", "Denmark", "🇩🇰")
            "FI" -> RegionInfo("FI", "Finland", "🇫🇮")
            "WW" -> RegionInfo("WW", "Worldwide", "🌍")
            else -> RegionInfo(code, code, "🏴")
        }
    }
}

/**
 * Link di download per una ROM
 */
data class DownloadLink(
    val name: String,
    val type: String,
    val format: String,
    val url: String,
    val size: String?,
    val sourceId: String? = null, // ID della sorgente che ha fornito questo link
    val requiresWebView: Boolean = false, // Se true, l'URL richiede un WebView per gestire JavaScript/countdown
    val delaySeconds: Int? = null // Secondi da attendere prima di avviare il download (es. per validazione server)
)

/**
 * Categoria di piattaforme per esplorazione
 */
data class PlatformCategory(
    val id: String,
    val name: String,
    val platforms: List<PlatformInfo>,
    val icon: String // Material Icon name
)

/**
 * Filtri di ricerca
 */
data class SearchFilters(
    val query: String = "",
    val selectedPlatforms: List<String> = emptyList(),
    val selectedRegions: List<String> = emptyList(),
    val selectedFormats: List<String> = emptyList(),
    val selectedSources: List<String> = emptyList()
) {
    fun isEmpty(): Boolean = query.isEmpty() && 
                            selectedPlatforms.isEmpty() && 
                            selectedRegions.isEmpty() &&
                            selectedFormats.isEmpty() &&
                            selectedSources.isEmpty()
    
    fun hasActiveFilters(): Boolean = selectedPlatforms.isNotEmpty() || 
                                     selectedRegions.isNotEmpty() ||
                                     selectedFormats.isNotEmpty() ||
                                     selectedSources.isNotEmpty()
}

/**
 * Stato del download
 */
sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data class Pending(val romTitle: String) : DownloadStatus()
    data class InProgress(
        val romTitle: String, 
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadStatus()
    data class Completed(val romTitle: String, val filePath: String) : DownloadStatus()
    data class Failed(val romTitle: String, val error: String) : DownloadStatus()
    data class Paused(val romTitle: String, val progress: Int) : DownloadStatus()
}

/**
 * Download info per persistenza
 */
data class DownloadInfo(
    val id: String,
    val romSlug: String,
    val romTitle: String,
    val url: String,
    val fileName: String,
    val status: DownloadStatus,
    val createdAt: Long,
    val completedAt: Long? = null
)
