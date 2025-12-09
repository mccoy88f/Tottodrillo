package com.tottodrillo.data.mapper

import com.tottodrillo.domain.model.IgdbSearchResult
import com.tottodrillo.domain.model.Rom
import com.tottodrillo.domain.model.RegionInfo
import com.tottodrillo.domain.manager.IgdbPlatformMapper
import java.util.Calendar

/**
 * Mapper per convertire dati IGDB in Rom
 */
object IgdbMapper {
    /**
     * Converte un IgdbSearchResult in una Rom aggiornata
     * Sovrascrive i campi della ROM con i dati da IGDB
     */
    fun mapIgdbToRom(
        originalRom: Rom,
        igdbResult: IgdbSearchResult
    ): Rom {
        // Estrai anno dalla data di rilascio
        val year = igdbResult.firstReleaseDate?.let { timestamp ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp * 1000
            calendar.get(Calendar.YEAR)
        }
        
        // Costruisci lista coverUrls: box art come prima, poi screenshots
        val coverUrls = mutableListOf<String>()
        if (igdbResult.coverImageId != null) {
            coverUrls.add("https://images.igdb.com/igdb/image/upload/t_cover_big/${igdbResult.coverImageId}.jpg")
        }
        coverUrls.addAll(igdbResult.screenshots.take(3))
        
        // Developer e Publisher (primo di ogni lista)
        val developer = igdbResult.developers.firstOrNull()
        val publisher = igdbResult.publishers.firstOrNull()
        
        return originalRom.copy(
            title = igdbResult.name, // Sovrascrive il titolo
            coverUrl = if (igdbResult.coverImageId != null) {
                "https://images.igdb.com/igdb/image/upload/t_cover_big/${igdbResult.coverImageId}.jpg"
            } else originalRom.coverUrl,
            coverUrls = if (coverUrls.isNotEmpty()) coverUrls else originalRom.coverUrls,
            igdbSummary = igdbResult.summary,
            igdbStoryline = igdbResult.storyline,
            igdbYear = year,
            igdbGenres = igdbResult.genres,
            igdbDeveloper = developer,
            igdbPublisher = publisher,
            igdbRating = igdbResult.rating,
            igdbScreenshots = igdbResult.screenshots,
            igdbUrl = igdbResult.url
        )
    }
    
    /**
     * Crea un IgdbImportConfirmation per il dialog di conferma
     */
    fun createImportConfirmation(
        rom: Rom,
        igdbResult: IgdbSearchResult
    ): com.tottodrillo.domain.model.IgdbImportConfirmation {
        val igdbPlatforms = igdbResult.platforms.map { it.name }
        
        return com.tottodrillo.domain.model.IgdbImportConfirmation(
            romTitle = rom.title,
            igdbTitle = igdbResult.name,
            romPlatform = rom.platform.displayName,
            igdbPlatforms = igdbPlatforms,
            romRegions = rom.regions.map { it.displayName }
        )
    }
}

