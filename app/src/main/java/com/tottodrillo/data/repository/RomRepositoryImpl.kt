package com.tottodrillo.data.repository

import android.content.Context
import com.tottodrillo.data.mapper.toDomain
import com.tottodrillo.data.mapper.toRegionInfo
import com.tottodrillo.data.model.EntryResponse
import com.tottodrillo.data.remote.EntryRequestBody
import com.tottodrillo.data.remote.ApiService
import com.tottodrillo.data.remote.NetworkResult
import com.tottodrillo.data.remote.SearchRequestBody
import com.tottodrillo.data.remote.SourceApiAdapter
import com.tottodrillo.data.remote.SourceExecutor
import com.tottodrillo.data.remote.extractData
import com.tottodrillo.data.remote.getUserMessage
import com.tottodrillo.data.remote.safeApiCall
import com.tottodrillo.di.NetworkModule
import com.google.gson.Gson
import okhttp3.OkHttpClient
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.tottodrillo.domain.model.PlatformInfo
import com.tottodrillo.domain.model.RegionInfo
import com.tottodrillo.domain.model.Rom
import com.tottodrillo.domain.model.SearchFilters
import com.tottodrillo.domain.repository.RomRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementazione del RomRepository
 * Gestisce il recupero dati dall'API e dalla cache locale
 */
@Singleton
class RomRepositoryImpl @Inject constructor(
    private val apiService: ApiService?, // Opzionale, deprecato - usa SourceManager invece
    @ApplicationContext private val context: Context,
    private val configRepository: DownloadConfigRepository,
    private val platformManager: com.tottodrillo.domain.manager.PlatformManager,
    private val sourceManager: com.tottodrillo.domain.manager.SourceManager,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
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
        // Verifica se ci sono sorgenti installate
        val hasSources = sourceManager.hasInstalledSources()
        if (!hasSources) {
            return NetworkResult.Error(
                com.tottodrillo.data.remote.NetworkException.UnknownError(
                    "Nessuna sorgente installata. Installa almeno una sorgente per utilizzare l'app."
                )
            )
        }
        
        return try {
            // Ottieni tutte le sorgenti abilitate
            var enabledSources = sourceManager.getEnabledSources()
            if (enabledSources.isEmpty()) {
                return NetworkResult.Error(
                    com.tottodrillo.data.remote.NetworkException.UnknownError(
                        "Nessuna sorgente abilitata"
                    )
                )
            }
            
            // Filtra le sorgenti in base al filtro selectedSources se presente
            if (filters.selectedSources.isNotEmpty()) {
                enabledSources = enabledSources.filter { it.id in filters.selectedSources }
                if (enabledSources.isEmpty()) {
                    return NetworkResult.Error(
                        com.tottodrillo.data.remote.NetworkException.UnknownError(
                            "Nessuna sorgente selezionata disponibile"
                        )
                    )
                }
            }
            
            // Cerca in tutte le sorgenti in parallelo
            val allRoms = coroutineScope {
                enabledSources.map { source ->
                    async {
                        try {
                            val sourceDir = File(source.installPath ?: return@async emptyList())
                            val metadata = sourceManager.getSourceMetadata(source.id)
                                ?: return@async emptyList()
                            
                            val executor = SourceExecutor.create(
                                metadata,
                                sourceDir,
                                okHttpClient,
                                gson
                            )
                            
                            // Normalizza i codici piattaforma a minuscolo per il mapping corretto
                            val platformsList = filters.selectedPlatforms.takeIf { it.isNotEmpty() }?.map { it.lowercase() } ?: emptyList()
                            android.util.Log.d("RomRepositoryImpl", "🔍 Ricerca in sorgente ${source.id}: query='${filters.query}', platforms=$platformsList, page=$page")
                            
                            val result = executor.searchRoms(
                                searchKey = filters.query.takeIf { it.isNotEmpty() },
                                platforms = platformsList,
                                regions = filters.selectedRegions.takeIf { it.isNotEmpty() } ?: emptyList(),
                                maxResults = 50,
                                page = page
                            )
                            
                            result.fold(
                                onSuccess = { searchResults ->
                                    android.util.Log.d("RomRepositoryImpl", "✅ Sorgente ${source.id}: ${searchResults.results.size} risultati trovati")
                                    val roms = searchResults.results.map { entry ->
                                        entry?.toDomain(sourceId = source.id)
                                    }
                                    // Log per verificare se le immagini sono presenti
                                    val romsWithImages = roms.filterNotNull().filter { it.coverUrl != null }
                                    if (romsWithImages.isNotEmpty()) {
                                        android.util.Log.d("RomRepositoryImpl", "🖼️ ${romsWithImages.size} ROM con immagini (es. ${romsWithImages.first().title}: ${romsWithImages.first().coverUrl})")
                                    } else {
                                        android.util.Log.w("RomRepositoryImpl", "⚠️ Nessuna ROM con immagini nella ricerca")
                                    }
                                    roms
                                },
                                onFailure = {
                                    android.util.Log.e("RomRepositoryImpl", "❌ Errore ricerca in sorgente ${source.id}", it)
                                    emptyList()
                                }
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("RomRepositoryImpl", "Errore ricerca in sorgente ${source.id}", e)
                            emptyList()
                        }
                    }
                }.awaitAll()
            }.flatten()
            
            // Filtra ROM nulle e raggruppa per slug
            val validRoms = allRoms.filterNotNull()
            val romsBySlug = validRoms.groupBy { it.slug }
            
            val enrichedRoms = romsBySlug.map { (slug, roms) ->
                // Prendi la prima ROM come base (nome, immagine principale, sourceId)
                val firstRom = roms.first()
                
                // Raccogli tutte le immagini (coverUrl e coverUrls) da tutte le ROM
                val allCoverUrls = roms
                    .flatMap { rom -> 
                        // Combina coverUrl e coverUrls
                        val urls = mutableListOf<String>()
                        rom.coverUrl?.let { urls.add(it) }
                        urls.addAll(rom.coverUrls)
                        urls
                    }
                    .distinct()
                
                // Unisci tutti i downloadLinks da tutte le ROM
                val allDownloadLinks = roms
                    .flatMap { it.downloadLinks }
                    .distinctBy { it.url } // Rimuovi link duplicati (stesso URL)
                
                // Unisci tutte le regioni
                val allRegions = roms
                    .flatMap { it.regions }
                    .distinctBy { it.code }
                
                // Arricchisci PlatformInfo
                val enrichedPlatform = enrichPlatformInfo(firstRom.platform, firstRom.sourceId)
                
                firstRom.copy(
                    platform = enrichedPlatform,
                    coverUrl = allCoverUrls.firstOrNull(), // Prima immagine come principale
                    coverUrls = allCoverUrls, // Tutte le immagini per il carosello
                    downloadLinks = allDownloadLinks,
                    regions = allRegions,
                    isFavorite = isFavorite(slug),
                    sourceId = firstRom.sourceId // SourceId della prima ROM
                )
            }
            
            NetworkResult.Success(enrichedRoms)
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nella ricerca ROM", e)
            NetworkResult.Error(
                com.tottodrillo.data.remote.NetworkException.UnknownError(
                    e.message ?: "Errore nella ricerca"
                )
            )
        }
    }

    override suspend fun getPlatforms(): NetworkResult<List<PlatformInfo>> {
        // Ritorna cache se disponibile
        platformsCache?.let { 
            return NetworkResult.Success(it) 
        }

        // Carica le piattaforme da tutte le sorgenti abilitate
        return try {
            val enabledSources = sourceManager.getEnabledSources()
            if (enabledSources.isEmpty()) {
                return NetworkResult.Error(
                    com.tottodrillo.data.remote.NetworkException.UnknownError(
                        "Nessuna sorgente abilitata"
                    )
                )
            }
            
            val allPlatforms = mutableMapOf<String, PlatformInfo>() // Usa mother_code come chiave per evitare duplicati
            
            // Carica le piattaforme solo dalle sorgenti abilitate
            for (source in enabledSources) {
                try {
                    val platforms = platformManager.loadPlatforms(source.id)
                    // Unisci le piattaforme, evitando duplicati per mother_code
                    platforms.forEach { platform ->
                        // Trova il mother_code per questa piattaforma
                        val motherCode = platformManager.getMotherCodeFromSourceCode(platform.code, source.id)
                        if (motherCode != null) {
                            // Se non esiste già o se questa sorgente ha dati migliori, aggiorna
                            if (!allPlatforms.containsKey(motherCode) || 
                                (allPlatforms[motherCode]?.displayName.isNullOrBlank() && !platform.displayName.isNullOrBlank())) {
                                allPlatforms[motherCode] = platform
                            }
                        } else {
                            // Se non troviamo il mother_code, aggiungiamo comunque la piattaforma
                            allPlatforms[platform.code] = platform
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RomRepositoryImpl", "Errore nel caricamento piattaforme per sorgente ${source.id}", e)
                }
            }
            
            val platformsList = allPlatforms.values.toList()
            platformsCache = platformsList // Salva in cache
            NetworkResult.Success(platformsList)
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel caricamento piattaforme", e)
            NetworkResult.Error(
                com.tottodrillo.data.remote.NetworkException.UnknownError(
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

        // Verifica se ci sono sorgenti installate
        val hasSources = sourceManager.hasInstalledSources()
        if (!hasSources) {
            return NetworkResult.Error(
                com.tottodrillo.data.remote.NetworkException.UnknownError(
                    "Nessuna sorgente installata"
                )
            )
        }
        
        val service = apiService ?: return NetworkResult.Error(
            com.tottodrillo.data.remote.NetworkException.UnknownError(
                "Nessuna sorgente configurata"
            )
        )
        
        return when (val result = safeApiCall { service.getRegions() }.extractData()) {
            is NetworkResult.Success<*> -> {
                val data = result.data as com.tottodrillo.data.model.RegionsResponse
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
        android.util.Log.d("RomRepositoryImpl", "🔍 getRomsByPlatform: platform=$platform, page=$page, limit=$limit")
        // Usa searchRoms con filtro piattaforma (stesso codice di aggregazione)
        val result = searchRoms(
            filters = SearchFilters(selectedPlatforms = listOf(platform)),
            page = page
        )
        when (result) {
            is NetworkResult.Success -> {
                android.util.Log.d("RomRepositoryImpl", "✅ getRomsByPlatform success: ${result.data.size} ROM trovate")
            }
            is NetworkResult.Error -> {
                android.util.Log.e("RomRepositoryImpl", "❌ getRomsByPlatform error: ${result.exception.getUserMessage()}")
            }
            is NetworkResult.Loading -> {
                android.util.Log.d("RomRepositoryImpl", "⏳ getRomsByPlatform loading...")
            }
        }
        return result
    }

    override suspend fun getRomBySlug(slug: String): NetworkResult<Rom> {
        val hasSources = sourceManager.hasInstalledSources()
        if (!hasSources) {
            return NetworkResult.Error(
                com.tottodrillo.data.remote.NetworkException.UnknownError(
                    "Nessuna sorgente installata"
                )
            )
        }
        
        return try {
            // Cerca in tutte le sorgenti abilitate
            val enabledSources = sourceManager.getEnabledSources()
            if (enabledSources.isEmpty()) {
                return NetworkResult.Error(
                    com.tottodrillo.data.remote.NetworkException.UnknownError(
                        "Nessuna sorgente abilitata"
                    )
                )
            }
            
            // Cerca in parallelo in tutte le sorgenti
            val results = coroutineScope {
                enabledSources.map { source ->
                    async {
                        try {
                            val sourceDir = File(source.installPath ?: return@async null)
                            val metadata = sourceManager.getSourceMetadata(source.id)
                                ?: return@async null
                            
                            val executor = SourceExecutor.create(
                                metadata,
                                sourceDir,
                                okHttpClient,
                                gson
                            )
                            
                            val result = executor.getEntry(slug)
                            result.fold(
                                onSuccess = { entryResponse ->
                                    // Verifica che entry non sia null prima di chiamare toDomain()
                                    val rom = entryResponse.entry?.toDomain(sourceId = source.id)
                                    if (rom != null) {
                                        android.util.Log.d("RomRepositoryImpl", "🖼️ getEntry per ${rom.title}: ${rom.coverUrls.size} immagini - ${rom.coverUrls}")
                                    }
                                    rom
                                },
                                onFailure = {
                                    android.util.Log.e("RomRepositoryImpl", "Errore getEntry in sorgente ${source.id}", it)
                                    null
                                }
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("RomRepositoryImpl", "Errore getEntry in sorgente ${source.id}", e)
                            null
                        }
                    }
                }.awaitAll()
            }
            
            // Filtra risultati nulli
            val foundRoms = results.filterNotNull()
            
            if (foundRoms.isEmpty()) {
                return NetworkResult.Error(
                    com.tottodrillo.data.remote.NetworkException.UnknownError(
                        "ROM non trovata in nessuna sorgente"
                    )
                )
            }
            
            // Se ci sono più ROM (da più sorgenti), uniscile
            val firstRom = foundRoms.first()
            
            // Raccogli tutte le immagini (coverUrl e coverUrls) da tutte le ROM
            val allCoverUrls = foundRoms
                .flatMap { rom -> 
                    // Combina coverUrl e coverUrls
                    val urls = mutableListOf<String>()
                    rom.coverUrl?.let { urls.add(it) }
                    urls.addAll(rom.coverUrls)
                    urls
                }
                .distinct()
            
            android.util.Log.d("RomRepositoryImpl", "🖼️ getRomBySlug: unite ${foundRoms.size} ROM, totale immagini: ${allCoverUrls.size} - ${allCoverUrls}")
            
            // Unisci tutti i downloadLinks da tutte le ROM
            val allDownloadLinks = foundRoms
                .flatMap { it.downloadLinks }
                .distinctBy { it.url } // Rimuovi link duplicati (stesso URL)
            
            // Unisci tutte le regioni
            val allRegions = foundRoms
                .flatMap { it.regions }
                .distinctBy { it.code }
            
            // Arricchisci con dati locali
            val enrichedPlatform = enrichPlatformInfo(firstRom.platform, firstRom.sourceId)
            val enrichedRom = firstRom.copy(
                platform = enrichedPlatform,
                coverUrl = allCoverUrls.firstOrNull(), // Prima immagine come principale
                coverUrls = allCoverUrls, // Tutte le immagini per il carosello
                downloadLinks = allDownloadLinks,
                regions = allRegions,
                isFavorite = isFavorite(firstRom.slug),
                sourceId = firstRom.sourceId // SourceId della prima ROM
            )
            
            NetworkResult.Success(enrichedRom)
        } catch (e: Exception) {
            android.util.Log.e("RomRepositoryImpl", "Errore nel recupero ROM", e)
            NetworkResult.Error(
                com.tottodrillo.data.remote.NetworkException.UnknownError(
                    e.message ?: "Errore nel recupero ROM"
                )
            )
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
     * Arricchisce PlatformInfo con i dati locali dal PlatformManager
     * Sostituisce completamente i dati con quelli locali (nome, brand, immagine, descrizione)
     * per rendere il sistema indipendente dalla sorgente API
     */
    private suspend fun enrichPlatformInfo(platformInfo: PlatformInfo, sourceId: String? = null): PlatformInfo {
        return try {
            // Il codice in platformInfo potrebbe essere:
            // 1. Un codice sorgente (es. "32x" da CrocDB) -> dobbiamo trovare il mother_code
            // 2. Un mother_code (es. "nes" da Vimm's Lair) -> possiamo usarlo direttamente
            
            // Prova prima a vedere se è un mother_code cercando in tutte le sorgenti
            val installedSources = sourceManager.getInstalledSources()
            var motherCode: String? = null
            
            // Se abbiamo sourceId, prova a trovare il mother_code da quella sorgente
            if (sourceId != null) {
                motherCode = platformManager.getMotherCodeFromSourceCode(platformInfo.code, sourceId)
            }
            
            // Se non trovato, prova in tutte le sorgenti
            if (motherCode == null) {
                for (source in installedSources) {
                    motherCode = platformManager.getMotherCodeFromSourceCode(platformInfo.code, source.id)
                    if (motherCode != null) break
                }
            }
            
            // Se non è un codice sorgente, potrebbe essere già un mother_code
            val finalMotherCode = motherCode ?: platformInfo.code
            
            // Carica platforms_main.json per ottenere i dati della piattaforma
            val platformsMain = platformManager.loadPlatformsMain()
            val motherPlatform = platformsMain.platforms.find { it.motherCode == finalMotherCode }
            
            if (motherPlatform != null) {
                // Usa i dati locali da platforms_main.json
                platformInfo.copy(
                    code = finalMotherCode, // Usa il mother_code
                    displayName = motherPlatform.name ?: motherPlatform.motherCode, // Nome locale
                    manufacturer = motherPlatform.brand, // Brand locale
                    imagePath = motherPlatform.image, // Immagine locale
                    description = motherPlatform.description // Descrizione locale
                )
            } else {
                // Se non trovata in platforms_main.json, mantieni i dati originali
                // Log solo se non è un codice sorgente non mappato (evita spam di warning)
                if (finalMotherCode != platformInfo.code || sourceId != null) {
                    android.util.Log.w("RomRepositoryImpl", "Piattaforma $finalMotherCode non trovata in platforms_main.json (source: $sourceId, original code: ${platformInfo.code})")
                }
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
    override fun clearCache() {
        platformsCache = null
        regionsCache = null
    }
}
