package com.tottodrillo.domain.repository

import com.tottodrillo.data.remote.NetworkResult
import com.tottodrillo.domain.model.PlatformInfo
import com.tottodrillo.domain.model.RegionInfo
import com.tottodrillo.domain.model.Rom
import com.tottodrillo.domain.model.SearchFilters
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface per accedere ai dati delle ROM
 * Definisce il contratto tra data layer e domain layer
 */
interface RomRepository {
    
    /**
     * Cerca ROM in base ai filtri
     */
    suspend fun searchRoms(
        filters: SearchFilters,
        page: Int = 1
    ): NetworkResult<List<Rom>>
    
    /**
     * Ottieni tutte le piattaforme disponibili
     */
    suspend fun getPlatforms(): NetworkResult<List<PlatformInfo>>
    
    /**
     * Ottieni tutte le regioni disponibili
     */
    suspend fun getRegions(): NetworkResult<List<RegionInfo>>
    
    /**
     * Ottieni ROM specifiche per piattaforma
     */
    suspend fun getRomsByPlatform(
        platform: String,
        page: Int = 1,
        limit: Int = 50
    ): NetworkResult<List<Rom>>
    
    /**
     * Ottieni il dettaglio di una ROM a partire dallo slug
     * @param includeDownloadLinks Se false, i download links non vengono caricati (utile per home screen)
     */
    suspend fun getRomBySlug(slug: String, includeDownloadLinks: Boolean = true): NetworkResult<Rom>
    
    /**
     * Stream di ROM preferite (Flow per reattività)
     */
    fun getFavoriteRoms(): Flow<List<Rom>>
    
    /**
     * Aggiungi ROM ai preferiti
     */
    suspend fun addToFavorites(rom: Rom): Result<Unit>
    
    /**
     * Rimuovi ROM dai preferiti
     */
    suspend fun removeFromFavorites(romSlug: String): Result<Unit>
    
    /**
     * Verifica se una ROM è nei preferiti
     */
    suspend fun isFavorite(romSlug: String): Boolean
    
    /**
     * Traccia quando una ROM viene aperta (per la sezione Recenti)
     */
    suspend fun trackRomOpened(romSlug: String)
    
    /**
     * Stream di ROM recenti (ultime 25 aperte)
     */
    fun getRecentRoms(): Flow<List<Rom>>
    
    /**
     * Stream di ROM scaricate e/o installate
     * Recupera le ROM basandosi sui file .status nella directory di download
     */
    fun getDownloadedRoms(): Flow<List<Rom>>
    
    /**
     * Pulisce la cache delle piattaforme e regioni
     * Utile quando cambiano le sorgenti abilitate
     */
    fun clearCache()
}
