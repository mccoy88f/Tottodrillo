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
    private var sourceMapping: Map<String, List<String>>? = null // mother_code -> lista codici CrocDB
    
    companion object {
        // I file sono nella root del progetto, li leggiamo dalle assets
        private const val PLATFORMS_MAIN_FILE = "platforms_main.json"
        private const val SOURCE_SETTING_FILE = "sources/crocdb.sourcesetting"
    }
    
    /**
     * Carica tutte le piattaforme dal file platforms_main.json
     * e le mappa con crocdb.sourcesetting per ottenere i codici CrocDB
     */
    suspend fun loadPlatforms(): List<PlatformInfo> = withContext(Dispatchers.IO) {
        // Usa cache se disponibile
        platformsCache?.let { return@withContext it }
        
        try {
            // Carica platforms_main.json
            val platformsMain = loadPlatformsMain()
            
            // Carica crocdb.sourcesetting
            val sourceMapping = loadSourceMapping()
            
            // Mappa le piattaforme madre a PlatformInfo con codici CrocDB
            val platforms = platformsMain.platforms.mapNotNull { motherPlatform ->
                // Trova i codici CrocDB corrispondenti al mother_code
                val crocdbCodes = sourceMapping[motherPlatform.motherCode] ?: emptyList()
                
                // Se non c'è mapping, salta questa piattaforma (non è disponibile su CrocDB)
                if (crocdbCodes.isEmpty()) {
                    null
                } else {
                    // Crea una PlatformInfo per ogni codice CrocDB
                    // Per ora creiamo solo la prima, ma potremmo crearne multiple
                    val crocdbCode = crocdbCodes.first()
                    PlatformInfo(
                        code = crocdbCode, // Codice CrocDB per le query API
                        displayName = motherPlatform.name ?: motherPlatform.motherCode,
                        manufacturer = motherPlatform.brand,
                        imagePath = motherPlatform.image,
                        description = motherPlatform.description
                    )
                }
            }
            
            platformsCache = platforms
            this@PlatformManager.sourceMapping = sourceMapping
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
     * Carica crocdb.sourcesetting dalle assets e crea il mapping mother_code -> codici CrocDB
     */
    private suspend fun loadSourceMapping(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(SOURCE_SETTING_FILE)
            val json = inputStream.bufferedReader().use { it.readText() }
            val sourceSetting = gson.fromJson(json, SourceSetting::class.java)
            
            // Estrai il mapping per CrocDB
            val crocdbConfig = sourceSetting.sources["crocdb"]
                ?: throw IllegalStateException("Configurazione CrocDB non trovata")
            
            // Converte il mapping in Map<String, List<String>>
            // Il mapping può contenere String o List<String>
            val mapping = mutableMapOf<String, List<String>>()
            
            crocdbConfig.mapping.forEach { (motherCode, crocdbValue) ->
                val crocdbCodes = when (crocdbValue) {
                    is String -> listOf(crocdbValue)
                    is List<*> -> crocdbValue.filterIsInstance<String>()
                    else -> emptyList()
                }
                if (crocdbCodes.isNotEmpty()) {
                    mapping[motherCode] = crocdbCodes
                }
            }
            
            mapping
        } catch (e: Exception) {
            android.util.Log.e("PlatformManager", "Errore nel caricamento crocdb.sourcesetting", e)
            throw e
        }
    }
    
    /**
     * Ottiene il codice CrocDB per un mother_code
     */
    suspend fun getCrocdbCode(motherCode: String): String? {
        val mapping = sourceMapping ?: loadSourceMapping()
        return mapping[motherCode]?.firstOrNull()
    }
    
    /**
     * Ottiene tutti i codici CrocDB per un mother_code (può essere multiplo)
     */
    suspend fun getCrocdbCodes(motherCode: String): List<String> {
        val mapping = sourceMapping ?: loadSourceMapping()
        return mapping[motherCode] ?: emptyList()
    }
    
    /**
     * Ottiene il mother_code da un codice CrocDB (reverse lookup)
     */
    suspend fun getMotherCodeFromCrocDbCode(crocDbCode: String): String? {
        val mapping = sourceMapping ?: loadSourceMapping()
        // Cerca il mother_code che contiene questo codice CrocDB
        return mapping.entries.firstOrNull { entry ->
            entry.value.contains(crocDbCode)
        }?.key
    }
    
    /**
     * Pulisce la cache
     */
    fun clearCache() {
        platformsCache = null
        sourceMapping = null
    }
}

