package com.crocdb.friends.data.repository

import android.content.Context
import com.crocdb.friends.data.mapper.toDomain
import com.crocdb.friends.data.mapper.toRegionInfo
import com.crocdb.friends.data.model.EntryResponse
import com.crocdb.friends.data.remote.EntryRequestBody
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementazione del RomRepository
 * Gestisce il recupero dati dall'API e dalla cache locale
 */
@Singleton
class RomRepositoryImpl @Inject constructor(
    private val apiService: CrocdbApiService,
    @ApplicationContext private val context: Context,
    private val configRepository: DownloadConfigRepository,
    private val platformManager: com.crocdb.friends.domain.manager.PlatformManager
) : RomRepository {

    // Cache in memoria per le piattaforme (evita chiamate ripetute)
    private var platformsCache: List<PlatformInfo>? = null
    private var regionsCache: List<RegionInfo>? = null
    
    companion object {
        private const val FAVORITES_FILE_NAME = "tottodrillo-favorites.status"
        private const val RECENT_FILE_NAME = "tottodrillo-recent.status"
        private const val MAX_RECENT_ROMS = 25
    }
    
    /**
     * Ottiene il percorso del file di stato (favoriti o recenti)
     */
    private suspend fun getStatusFilePath(fileName: String): File {
        val config = configRepository.downloadConfig.first()
        return File(config.downloadPath, fileName)
    }

    override suspend fun searchRoms(
        filters: SearchFilters,
        page: Int
    ): NetworkResult<List<Rom>> {
        val requestBody = SearchRequestBody(
            search_key = filters.query.takeIf { it.isNotEmpty() },
            platforms = filters.selectedPlatforms.takeIf { it.isNotEmpty() } ?: emptyList(),
            regions = filters.selectedRegions.takeIf { it.isNotEmpty() } ?: emptyList(),
            max_results = 50,
            page = page
        )

        return when (val result = safeApiCall { apiService.searchRoms(requestBody) }.extractData()) {
            is NetworkResult.Success -> {
                val roms = result.data.results.map { it.toDomain() }
                    .map { rom -> 
                        // Arricchisci PlatformInfo con l'immagine
                        val enrichedPlatform = enrichPlatformInfo(rom.platform)
                        rom.copy(
                            platform = enrichedPlatform,
                            isFavorite = isFavorite(rom.slug)
                        )
                    }
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

        // Usa PlatformManager per caricare le piattaforme dai file JSON locali
        return try {
            val platforms = platformManager.loadPlatforms()
            platformsCache = platforms // Salva in cache
            NetworkResult.Success(platforms)
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel caricamento piattaforme", e)
            NetworkResult.Error(
                com.crocdb.friends.data.remote.NetworkException.UnknownError(
                    e.message ?: "Errore nel caricamento piattaforme"
                )
            )
        }
    }

    override suspend fun getRegions(): NetworkResult<List<RegionInfo>> {
        // Ritorna cache se disponibile
        regionsCache?.let { 
            return NetworkResult.Success(it) 
        }

        return when (val result = safeApiCall { apiService.getRegions() }.extractData()) {
            is NetworkResult.Success<*> -> {
                val data = result.data as com.crocdb.friends.data.model.RegionsResponse
                val regions = data.regions.map { it.toRegionInfo() }
                regionsCache = regions // Salva in cache
                NetworkResult.Success(regions)
            }
            is NetworkResult.Error -> result
            is NetworkResult.Loading -> NetworkResult.Loading
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
                    .map { rom -> 
                        // Arricchisci PlatformInfo con l'immagine
                        val enrichedPlatform = enrichPlatformInfo(rom.platform)
                        rom.copy(
                            platform = enrichedPlatform,
                            isFavorite = isFavorite(rom.slug)
                        )
                    }
                NetworkResult.Success(roms)
            }
            is NetworkResult.Error -> result
            is NetworkResult.Loading -> result
        }
    }

    override suspend fun getRomBySlug(slug: String): NetworkResult<Rom> {
        val requestBody = EntryRequestBody(slug = slug)

        return when (val result = safeApiCall { apiService.getEntry(requestBody) }.extractData()) {
            is NetworkResult.Success<*> -> {
                val data = result.data as EntryResponse
                val rom = data.entry.toDomain()
                // Arricchisci PlatformInfo con l'immagine
                val enrichedPlatform = enrichPlatformInfo(rom.platform)
                val enrichedRom = rom.copy(
                    platform = enrichedPlatform,
                    isFavorite = isFavorite(data.entry.slug)
                )
                NetworkResult.Success(enrichedRom)
            }
            is NetworkResult.Error -> result
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    override fun getFavoriteRoms(): Flow<List<Rom>> = flow {
        // Carica i favoriti dal file .status
        val favoriteSlugs = loadFavoritesFromFile()
        
        if (favoriteSlugs.isEmpty()) {
        emit(emptyList())
            return@flow
        }
        
        // Carica i dettagli per ogni ROM preferita
        val favoriteRoms = mutableListOf<Rom>()
        for (slug in favoriteSlugs) {
            try {
                when (val result = getRomBySlug(slug)) {
                    is NetworkResult.Success -> {
                        favoriteRoms.add(result.data)
                    }
                    else -> {
                        // Se una ROM non viene trovata (es. eliminata), continua con le altre
                    }
                }
            } catch (e: Exception) {
                // Ignora errori per singole ROM e continua
            }
        }
        
        emit(favoriteRoms)
    }

    override suspend fun addToFavorites(rom: Rom): Result<Unit> {
        return try {
            val favoriteSlugs = loadFavoritesFromFile().toMutableSet()
            favoriteSlugs.add(rom.slug)
            saveFavoritesToFile(favoriteSlugs.toList())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFromFavorites(romSlug: String): Result<Unit> {
        return try {
            val favoriteSlugs = loadFavoritesFromFile().toMutableSet()
            favoriteSlugs.remove(romSlug)
            saveFavoritesToFile(favoriteSlugs.toList())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isFavorite(romSlug: String): Boolean {
        val favoriteSlugs = loadFavoritesFromFile()
        return favoriteSlugs.contains(romSlug)
    }
    
    override suspend fun trackRomOpened(romSlug: String) {
        try {
            val recentEntries = loadRecentFromFile().toMutableList()
            
            // Rimuovi eventuali duplicati di questo slug
            recentEntries.removeAll { it.first == romSlug }
            
            // Aggiungi in cima con timestamp corrente
            recentEntries.add(0, Pair(romSlug, System.currentTimeMillis()))
            
            // Mantieni solo le ultime MAX_RECENT_ROMS
            val trimmedEntries = recentEntries.take(MAX_RECENT_ROMS)
            
            saveRecentToFile(trimmedEntries)
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel tracciamento ROM aperta", e)
        }
    }
    
    override fun getRecentRoms(): Flow<List<Rom>> = flow {
        // Carica le ROM recenti dal file .status
        val recentEntries = loadRecentFromFile()
        
        if (recentEntries.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        
        // Carica i dettagli per ogni ROM recente (mantenendo l'ordine)
        val recentRoms = mutableListOf<Rom>()
        for ((slug, _) in recentEntries) {
            try {
                when (val result = getRomBySlug(slug)) {
                    is NetworkResult.Success -> {
                        recentRoms.add(result.data)
                    }
                    else -> {
                        // Se una ROM non viene trovata (es. eliminata), continua con le altre
                    }
                }
            } catch (e: Exception) {
                // Ignora errori per singole ROM e continua
            }
        }
        
        emit(recentRoms)
    }
    
    /**
     * Carica i favoriti dal file .status
     * Formato: una riga per slug
     */
    private suspend fun loadFavoritesFromFile(): List<String> {
        return try {
            val file = getStatusFilePath(FAVORITES_FILE_NAME)
            if (file.exists() && file.isFile) {
                file.readLines().filter { it.isNotBlank() }.map { it.trim() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel caricamento favoriti", e)
            emptyList()
        }
    }
    
    /**
     * Salva i favoriti nel file .status
     * Formato: una riga per slug
     */
    private suspend fun saveFavoritesToFile(slugs: List<String>) {
        try {
            val file = getStatusFilePath(FAVORITES_FILE_NAME)
            // Assicura che la directory esista
            file.parentFile?.mkdirs()
            file.writeText(slugs.joinToString("\n"))
            android.util.Log.d("RomRepositoryImpl", "✅ Favoriti salvati: ${slugs.size} ROM")
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel salvataggio favoriti", e)
            throw e
        }
    }
    
    /**
     * Carica le ROM recenti dal file .status
     * Formato: una riga per entry, formato: <slug>\t<timestamp>
     */
    private suspend fun loadRecentFromFile(): List<Pair<String, Long>> {
        return try {
            val file = getStatusFilePath(RECENT_FILE_NAME)
            if (file.exists() && file.isFile) {
                file.readLines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.contains('\t')) {
                            val parts = trimmed.split('\t', limit = 2)
                            if (parts.size == 2) {
                                val slug = parts[0]
                                val timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                                Pair(slug, timestamp)
                            } else {
                                null
                            }
                        } else {
                            // Formato retrocompatibile: solo slug, usa timestamp corrente
                            Pair(trimmed, System.currentTimeMillis())
                        }
                    }
                    .sortedByDescending { it.second } // Ordina per timestamp decrescente
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel caricamento ROM recenti", e)
            emptyList()
        }
    }
    
    /**
     * Salva le ROM recenti nel file .status
     * Formato: una riga per entry, formato: <slug>\t<timestamp>
     */
    private suspend fun saveRecentToFile(entries: List<Pair<String, Long>>) {
        try {
            val file = getStatusFilePath(RECENT_FILE_NAME)
            // Assicura che la directory esista
            file.parentFile?.mkdirs()
            val content = entries.joinToString("\n") { "${it.first}\t${it.second}" }
            file.writeText(content)
            android.util.Log.d("RomRepositoryImpl", "✅ ROM recenti salvate: ${entries.size} ROM")
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel salvataggio ROM recenti", e)
            throw e
        }
    }

    /**
     * Arricchisce PlatformInfo con l'immagine dal PlatformManager
     */
    private suspend fun enrichPlatformInfo(platformInfo: PlatformInfo): PlatformInfo {
        return try {
            // Carica tutte le piattaforme per trovare quella corrispondente
            val allPlatforms = platformManager.loadPlatforms()
            val matchingPlatform = allPlatforms.find { it.code == platformInfo.code }
            
            if (matchingPlatform != null && matchingPlatform.imagePath != null) {
                platformInfo.copy(imagePath = matchingPlatform.imagePath)
            } else {
                platformInfo
            }
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nell'arricchimento PlatformInfo", e)
            platformInfo
        }
    }
    
    /**
     * Pulisce la cache (utile per refresh)
     */
    fun clearCache() {
        platformsCache = null
        regionsCache = null
    }
}
