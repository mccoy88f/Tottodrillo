package com.tottodrillo.domain.manager

import android.content.Context
import com.tottodrillo.data.model.MotherPlatform
import com.tottodrillo.data.model.PlatformsMainResponse
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
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager
) {
    
    private val gson = Gson()
    private var platformsCache: List<PlatformInfo>? = null
    private var sourceMappingCache: Map<String, Map<String, List<String>>>? = null // source_name -> (mother_code -> lista codici)
    
    companion object {
        // I file sono nella root del progetto, li leggiamo dalle assets
        private const val PLATFORMS_MAIN_FILE = "platforms_main.json"
        private const val PLATFORM_MAPPING_FILE = "platform_mapping.json"
        private const val DEFAULT_SOURCE = "crocdb" // Sorgente predefinita
    }
    
    /**
     * Carica tutte le piattaforme per una sorgente specifica
     * I mapping vengono caricati dai file platform_mapping.json delle sorgenti installate
     */
    suspend fun loadPlatforms(sourceName: String = DEFAULT_SOURCE): List<PlatformInfo> = withContext(Dispatchers.IO) {
        try {
            // Carica i mapping dalle sorgenti installate
            val sourceMapping = loadSourceMappings()
            sourceMappingCache = sourceMapping
            
            // Carica platforms_main.json per ottenere i dati delle piattaforme (nome, brand, immagine, etc.)
            val platformsMain = loadPlatformsMain()
            val platformsMap = platformsMain.platforms.associateBy { it.motherCode }
            
            // Ottieni il mapping per la sorgente specificata
            val sourcePlatformMapping = sourceMapping[sourceName] ?: emptyMap()
            
            // Crea le PlatformInfo per ogni mother_code mappato
            val platforms = sourcePlatformMapping.mapNotNull { (motherCode, sourceCodes) ->
                val motherPlatform = platformsMap[motherCode]
                
                if (motherPlatform == null) {
                    android.util.Log.w("PlatformManager", "Mother code $motherCode non trovato in platforms_main.json")
                    // Crea comunque una PlatformInfo base se il mother_code non esiste
                    PlatformInfo(
                        code = sourceCodes.firstOrNull() ?: return@mapNotNull null,
                        displayName = motherCode.uppercase(),
                        manufacturer = null,
                        imagePath = null,
                        description = null
                    )
                } else {
                    // Usa il primo codice sorgente per la PlatformInfo
                    val sourceCode = sourceCodes.firstOrNull() ?: return@mapNotNull null
                    PlatformInfo(
                        code = sourceCode, // Codice sorgente per le query API
                        displayName = motherPlatform.name ?: motherPlatform.motherCode,
                        manufacturer = motherPlatform.brand,
                        imagePath = motherPlatform.image,
                        description = motherPlatform.description
                    )
                }
            }
            
            platformsCache = platforms
            platforms
        } catch (e: Exception) {
            android.util.Log.e("PlatformManager", "Errore nel caricamento piattaforme per sorgente $sourceName", e)
            emptyList()
        }
    }
    
    /**
     * Carica platforms_main.json dalle assets (metodo pubblico per uso esterno)
     */
    suspend fun loadPlatformsMain(): PlatformsMainResponse = withContext(Dispatchers.IO) {
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
     * Carica i mapping delle piattaforme dalle sorgenti installate
     * Ritorna: source_name -> (mother_code -> lista codici sorgente)
     */
    private suspend fun loadSourceMappings(): Map<String, Map<String, List<String>>> = withContext(Dispatchers.IO) {
        val mapping = mutableMapOf<String, MutableMap<String, List<String>>>()
        
        try {
            // Carica tutte le sorgenti installate
            val installedSources = sourceManager.getInstalledSources()
            
            for (source in installedSources) {
                val sourceDir = source.installPath?.let { File(it) } ?: continue
                val mappingFile = File(sourceDir, PLATFORM_MAPPING_FILE)
                
                if (!mappingFile.exists()) {
                    android.util.Log.w("PlatformManager", "File platform_mapping.json non trovato per sorgente ${source.id}")
                    continue
                }
                
                try {
                    val mappingJson = mappingFile.readText()
                    val mappingData = gson.fromJson(mappingJson, Map::class.java)
                    val platformMapping = mappingData["mapping"] as? Map<*, *> ?: continue
                    
                    // Crea il mapping per questa sorgente
                    val sourceMap = mapping.getOrPut(source.id) { mutableMapOf() }
                    
                    platformMapping.forEach { (motherCode, sourceCodes) ->
                        val motherCodeStr = motherCode.toString()
                        val codesList = when (sourceCodes) {
                            is String -> listOf(sourceCodes)
                            is List<*> -> sourceCodes.mapNotNull { it?.toString() }
                            else -> emptyList()
                        }
                        
                        if (codesList.isNotEmpty()) {
                            sourceMap[motherCodeStr] = codesList
                        }
                    }
                    
                    android.util.Log.d("PlatformManager", "Caricato mapping per sorgente ${source.id}: ${sourceMap.size} piattaforme")
                } catch (e: Exception) {
                    android.util.Log.e("PlatformManager", "Errore nel caricamento mapping per sorgente ${source.id}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlatformManager", "Errore nel caricamento mapping sorgenti", e)
        }
        
        return@withContext mapping
    }
    
    /**
     * Costruisce il mapping delle sorgenti dalle piattaforme caricate (metodo legacy per compatibilità)
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
        val mapping = sourceMappingCache ?: loadSourceMappings().also { sourceMappingCache = it }
        return mapping[sourceName]?.get(motherCode)?.firstOrNull()
    }
    
    /**
     * Ottiene tutti i codici sorgente per un mother_code (può essere multiplo)
     */
    suspend fun getSourceCodes(motherCode: String, sourceName: String = DEFAULT_SOURCE): List<String> {
        val mapping = sourceMappingCache ?: loadSourceMappings().also { sourceMappingCache = it }
        return mapping[sourceName]?.get(motherCode) ?: emptyList()
    }
    
    /**
     * Ottiene il mother_code da un codice sorgente (reverse lookup)
     */
    suspend fun getMotherCodeFromSourceCode(sourceCode: String, sourceName: String = DEFAULT_SOURCE): String? {
        val mapping = sourceMappingCache ?: loadSourceMappings().also { sourceMappingCache = it }
        // Cerca il mother_code che contiene questo codice sorgente (case-insensitive)
        val sourceCodeLower = sourceCode.lowercase()
        return mapping[sourceName]?.entries?.firstOrNull { entry ->
            entry.value.any { it.lowercase() == sourceCodeLower }
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
     * Ottiene tutte le sorgenti disponibili dalle sorgenti installate
     */
    suspend fun getAvailableSources(): Set<String> = withContext(Dispatchers.IO) {
        try {
            val mapping = sourceMappingCache ?: loadSourceMappings().also { sourceMappingCache = it }
            mapping.keys.toSet()
        } catch (e: Exception) {
            android.util.Log.e("PlatformManager", "Errore nel recupero sorgenti disponibili", e)
            emptySet()
        }
    }
}

