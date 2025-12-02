package com.crocdb.friends.data.repository

import com.crocdb.friends.data.mapper.toDomain
import com.crocdb.friends.data.remote.CrocdbApiService
import com.crocdb.friends.data.remote.NetworkResult
import com.crocdb.friends.data.remote.SearchRequestBody
import com.crocdb.friends.data.remote.extractData
import com.crocdb.friends.data.remote.safeApiCall
import com.crocdb.friends.domain.model.PlatformInfo
import com.crocdb.friends.domain.model.RegionInfo
import com.crocdb.friends.domain.model.Rom
import com.crocdb.friends.domain.model.SearchFilters
import com.crocdb.friends.domain.repository.RomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementazione del RomRepository
 * Gestisce il recupero dati dall'API e dalla cache locale
 */
@Singleton
class RomRepositoryImpl @Inject constructor(
    private val apiService: CrocdbApiService
    // TODO: Aggiungere FavoritesDao quando implementeremo Room
) : RomRepository {

    // Cache in memoria per le piattaforme (evita chiamate ripetute)
    private var platformsCache: List<PlatformInfo>? = null
    private var regionsCache: List<RegionInfo>? = null
    
    // Set temporaneo per i preferiti (sostituire con Room)
    private val favoritesSet = mutableSetOf<String>()

    override suspend fun searchRoms(
        filters: SearchFilters,
        page: Int
    ): NetworkResult<List<Rom>> {
        val requestBody = SearchRequestBody(
            search_key = filters.query.takeIf { it.isNotEmpty() },
            platforms = filters.selectedPlatforms.takeIf { it.isNotEmpty() },
            regions = filters.selectedRegions.takeIf { it.isNotEmpty() },
            max_results = 50,
            page = page
        )

        return when (val result = safeApiCall { apiService.searchRoms(requestBody) }.extractData()) {
            is NetworkResult.Success -> {
                val roms = result.data.results.map { it.toDomain() }
                    .map { rom -> rom.copy(isFavorite = isFavorite(rom.slug)) }
                NetworkResult.Success(roms)
            }
            is NetworkResult.Error -> result
            is NetworkResult.Loading -> result
        }
    }

    override suspend fun getPlatforms(): NetworkResult<List<PlatformInfo>> {
        // Ritorna cache se disponibile
        platformsCache?.let { 
            return NetworkResult.Success(it) 
        }

        return when (val result = safeApiCall { apiService.getPlatforms() }.extractData()) {
            is NetworkResult.Success -> {
                val platforms = result.data.map { it.toDomain() }
                platformsCache = platforms // Salva in cache
                NetworkResult.Success(platforms)
            }
            is NetworkResult.Error -> result
            is NetworkResult.Loading -> result
        }
    }

    override suspend fun getRegions(): NetworkResult<List<RegionInfo>> {
        // Ritorna cache se disponibile
        regionsCache?.let { 
            return NetworkResult.Success(it) 
        }

        return when (val result = safeApiCall { apiService.getRegions() }.extractData()) {
            is NetworkResult.Success -> {
                val regions = result.data.map { it.toDomain() }
                regionsCache = regions // Salva in cache
                NetworkResult.Success(regions)
            }
            is NetworkResult.Error -> result
            is NetworkResult.Loading -> result
        }
    }

    override suspend fun getRomsByPlatform(
        platform: String,
        page: Int,
        limit: Int
    ): NetworkResult<List<Rom>> {
        val requestBody = SearchRequestBody(
            platforms = listOf(platform),
            max_results = limit,
            page = page
        )

        return when (val result = safeApiCall { apiService.searchRoms(requestBody) }.extractData()) {
            is NetworkResult.Success -> {
                val roms = result.data.results.map { it.toDomain() }
                    .map { rom -> rom.copy(isFavorite = isFavorite(rom.slug)) }
                NetworkResult.Success(roms)
            }
            is NetworkResult.Error -> result
            is NetworkResult.Loading -> result
        }
    }

    override fun getFavoriteRoms(): Flow<List<Rom>> = flow {
        // TODO: Implementare con Room Database
        // Per ora ritorna lista vuota
        emit(emptyList())
    }

    override suspend fun addToFavorites(rom: Rom): Result<Unit> {
        return try {
            favoritesSet.add(rom.slug)
            // TODO: Salvare in Room Database
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFromFavorites(romSlug: String): Result<Unit> {
        return try {
            favoritesSet.remove(romSlug)
            // TODO: Rimuovere da Room Database
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isFavorite(romSlug: String): Boolean {
        // TODO: Verificare in Room Database
        return favoritesSet.contains(romSlug)
    }

    /**
     * Pulisce la cache (utile per refresh)
     */
    fun clearCache() {
        platformsCache = null
        regionsCache = null
    }
}
