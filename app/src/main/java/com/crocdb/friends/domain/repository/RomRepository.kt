package com.crocdb.friends.domain.repository

import com.crocdb.friends.data.remote.NetworkResult
import com.crocdb.friends.domain.model.PlatformInfo
import com.crocdb.friends.domain.model.RegionInfo
import com.crocdb.friends.domain.model.Rom
import com.crocdb.friends.domain.model.SearchFilters
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
}
