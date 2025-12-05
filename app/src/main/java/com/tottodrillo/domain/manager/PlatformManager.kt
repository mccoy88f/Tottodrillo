package com.tottodrillo.domain.manager

import android.content.Context
import com.tottodrillo.data.model.MotherPlatform
import com.tottodrillo.data.model.PlatformsMainResponse
import com.tottodrillo.data.model.SourceConfig
import com.tottodrillo.data.model.SourceSetting
import com.tottodrillo.domain.model.PlatformInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager per gestire le piattaforme dai file JSON locali
 */
@Singleton
class PlatformManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val gson = Gson()
    private var platformsCache: List<PlatformInfo>? = null
    private var sourceMappingCache: Map<String, Map<String, List<String>>>? = null // source_name -> (mother_code -> lista codici)
    
    companion object {
        // I file sono nella root del progetto, li leggiamo dalle assets
        private const val PLATFORMS_MAIN_FILE = "platforms_main.json"
        private const val DEFAULT_SOURCE = "crocdb" // Sorgente predefinita
    }
    
    /**
     * Carica tutte le piattaforme dal file platforms_main.json
     * I mapping delle sorgenti sono ora integrati direttamente nel file JSON
     */
    suspend fun loadPlatforms(sourceName: String = DEFAULT_SOURCE): List<PlatformInfo> = withContext(Dispatchers.IO) {
        // Usa cache se disponibile
        platformsCache?.let { return@withContext it }
        
        try {
            // Carica platforms_main.json
            val platformsMain = loadPlatformsMain()
            
            // Crea il mapping delle sorgenti per accesso rapido
            val sourceMapping = buildSourceMapping(platformsMain.platforms)
            sourceMappingCache = sourceMapping
            
            // Mappa le piattaforme madre a PlatformInfo usando i dati locali
            val platforms = platformsMain.platforms.mapNotNull { motherPlatform ->
                // Trova i codici per la sorgente specificata
                val sourceCodes = motherPlatform.sourceMappings[sourceName] ?: emptyList()
                
                // Se non c'è mapping per questa sorgente, salta questa piattaforma
                if (sourceCodes.isEmpty()) {
                    null
                } else {
                    // Crea una PlatformInfo per ogni codice sorgente
                    // Per ora creiamo solo la prima, ma potremmo crearne multiple
                    val sourceCode = sourceCodes.first()
                    PlatformInfo(
                        code = sourceCode, // Codice sorgente per le query API
                        displayName = motherPlatform.name ?: motherPlatform.motherCode, // Usa nome locale
                        manufacturer = motherPlatform.brand, // Usa brand locale
                        imagePath = motherPlatform.image, // Usa immagine locale
                        description = motherPlatform.description // Usa descrizione locale
                    )
                }
            }
            
            platformsCache = platforms
            platforms
        } catch (e: Exception) {
            android.util.Log.e("PlatformManager", "Errore nel caricamento piattaforme", e)
            emptyList()
        }
    }
    
    /**
     * Carica platforms_main.json dalle assets
     */
    private suspend fun loadPlatformsMain(): PlatformsMainResponse = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(PLATFORMS_MAIN_FILE)
            val json = inputStream.bufferedReader().use { it.readText() }
            gson.fromJson(json, PlatformsMainResponse::class.java)
        } catch (e: Exception) {
            android.util.Log.e("PlatformManager", "Errore nel caricamento platforms_main.json", e)
            throw e
        }
    }
    
    /**
     * Costruisce il mapping delle sorgenti dalle piattaforme caricate
     * Ritorna: source_name -> (mother_code -> lista codici)
     */
    private fun buildSourceMapping(platforms: List<MotherPlatform>): Map<String, Map<String, List<String>>> {
        val mapping = mutableMapOf<String, MutableMap<String, List<String>>>()
        
        platforms.forEach { platform ->
            platform.sourceMappings.forEach { (sourceName, codes) ->
                if (codes.isNotEmpty()) {
                    val sourceMap = mapping.getOrPut(sourceName) { mutableMapOf() }
                    sourceMap[platform.motherCode] = codes
                }
            }
        }
        
        return mapping
    }
    
    /**
     * Ottiene il codice sorgente per un mother_code
     */
    suspend fun getSourceCode(motherCode: String, sourceName: String = DEFAULT_SOURCE): String? {
        val mapping = sourceMappingCache ?: run {
            val platformsMain = loadPlatformsMain()
            buildSourceMapping(platformsMain.platforms)
        }
        return mapping[sourceName]?.get(motherCode)?.firstOrNull()
    }
    
    /**
     * Ottiene tutti i codici sorgente per un mother_code (può essere multiplo)
     */
    suspend fun getSourceCodes(motherCode: String, sourceName: String = DEFAULT_SOURCE): List<String> {
        val mapping = sourceMappingCache ?: run {
            val platformsMain = loadPlatformsMain()
            buildSourceMapping(platformsMain.platforms)
        }
        return mapping[sourceName]?.get(motherCode) ?: emptyList()
    }
    
    /**
     * Ottiene il mother_code da un codice sorgente (reverse lookup)
     */
    suspend fun getMotherCodeFromSourceCode(sourceCode: String, sourceName: String = DEFAULT_SOURCE): String? {
        val mapping = sourceMappingCache ?: run {
            val platformsMain = loadPlatformsMain()
            buildSourceMapping(platformsMain.platforms)
        }
        // Cerca il mother_code che contiene questo codice sorgente
        return mapping[sourceName]?.entries?.firstOrNull { entry ->
            entry.value.contains(sourceCode)
        }?.key
    }
    
    /**
     * Metodi di compatibilità per CrocDB (deprecati, usa i metodi generici)
     */
    @Deprecated("Usa getSourceCode invece", ReplaceWith("getSourceCode(motherCode, \"crocdb\")"))
    suspend fun getCrocdbCode(motherCode: String): String? = getSourceCode(motherCode, "crocdb")
    
    @Deprecated("Usa getSourceCodes invece", ReplaceWith("getSourceCodes(motherCode, \"crocdb\")"))
    suspend fun getCrocdbCodes(motherCode: String): List<String> = getSourceCodes(motherCode, "crocdb")
    
    @Deprecated("Usa getMotherCodeFromSourceCode invece", ReplaceWith("getMotherCodeFromSourceCode(crocDbCode, \"crocdb\")"))
    suspend fun getMotherCodeFromCrocDbCode(crocDbCode: String): String? = getMotherCodeFromSourceCode(crocDbCode, "crocdb")
    
    /**
     * Pulisce la cache
     */
    fun clearCache() {
        platformsCache = null
        sourceMappingCache = null
    }
    
    /**
     * Ottiene tutte le sorgenti disponibili dalle piattaforme caricate
     */
    suspend fun getAvailableSources(): Set<String> = withContext(Dispatchers.IO) {
        try {
            val platformsMain = loadPlatformsMain()
            platformsMain.platforms
                .flatMap { it.sourceMappings.keys }
                .toSet()
        } catch (e: Exception) {
            android.util.Log.e("PlatformManager", "Errore nel recupero sorgenti disponibili", e)
            emptySet()
        }
    }
}

