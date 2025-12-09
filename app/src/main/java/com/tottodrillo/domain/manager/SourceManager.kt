package com.tottodrillo.domain.manager

import android.content.Context
import com.google.gson.Gson
import com.tottodrillo.domain.model.InstalledSourceConfig
import com.tottodrillo.domain.model.Source
import com.tottodrillo.domain.model.SourceMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager per gestire le sorgenti installabili
 * Gestisce installazione, disinstallazione e validazione delle sorgenti
 */
@Singleton
class SourceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    
    companion object {
        private const val SOURCES_DIR = "sources"
        private const val METADATA_FILE = "source.json"
        private const val CONFIG_FILE = "installed_sources.json"
    }
    
    /**
     * Ottiene la directory delle sorgenti installate
     */
    private fun getSourcesDirectory(): File {
        return File(context.filesDir, SOURCES_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Ottiene la directory di una sorgente specifica
     */
    private fun getSourceDirectory(sourceId: String): File {
        return File(getSourcesDirectory(), sourceId)
    }
    
    /**
     * Ottiene il file di configurazione delle sorgenti installate
     */
    private fun getConfigFile(): File {
        return File(context.filesDir, CONFIG_FILE)
    }
    
    /**
     * Carica tutte le sorgenti installate
     */
    suspend fun getInstalledSources(): List<Source> = withContext(Dispatchers.IO) {
        try {
            val sourcesDir = getSourcesDirectory()
            if (!sourcesDir.exists()) {
                return@withContext emptyList()
            }
            
            val configs = loadInstalledConfigsInternal()
            val sources = mutableListOf<Source>()
            
            sourcesDir.listFiles()?.forEach { sourceDir ->
                if (sourceDir.isDirectory) {
                    val metadataFile = File(sourceDir, METADATA_FILE)
                    if (metadataFile.exists()) {
                        try {
                            val metadata = loadSourceMetadata(metadataFile)
                            val config = configs.find { it.sourceId == metadata.id }
                            
                            sources.add(
                                Source(
                                    id = metadata.id,
                                    name = metadata.name,
                                    version = metadata.version,
                                    description = metadata.description,
                                    author = metadata.author,
                                    baseUrl = metadata.baseUrl ?: "", // Può essere null per sorgenti non-API
                                    isInstalled = true,
                                    installPath = sourceDir.absolutePath,
                                    type = metadata.type // Aggiungi il tipo di sorgente
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("SourceManager", "Errore nel caricamento sorgente ${sourceDir.name}", e)
                        }
                    }
                }
            }
            
            sources
        } catch (e: Exception) {
            android.util.Log.e("SourceManager", "Errore nel caricamento sorgenti installate", e)
            emptyList()
        }
    }
    
    /**
     * Verifica se una sorgente è installata
     */
    suspend fun isSourceInstalled(sourceId: String): Boolean = withContext(Dispatchers.IO) {
        val sourceDir = getSourceDirectory(sourceId)
        val metadataFile = File(sourceDir, METADATA_FILE)
        metadataFile.exists()
    }
    
    /**
     * Ottiene i metadata di una sorgente installata
     */
    suspend fun getSourceMetadata(sourceId: String): SourceMetadata? = withContext(Dispatchers.IO) {
        try {
            val sourceDir = getSourceDirectory(sourceId)
            val metadataFile = File(sourceDir, METADATA_FILE)
            if (metadataFile.exists()) {
                loadSourceMetadata(metadataFile)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SourceManager", "Errore nel recupero metadata sorgente $sourceId", e)
            null
        }
    }
    
    /**
     * Carica i metadata da un file
     */
    private fun loadSourceMetadata(metadataFile: File): SourceMetadata {
        val json = metadataFile.readText()
        return gson.fromJson(json, SourceMetadata::class.java)
    }
    
    /**
     * Carica le configurazioni delle sorgenti installate
     */
    suspend fun loadInstalledConfigs(): List<InstalledSourceConfig> = withContext(Dispatchers.IO) {
        loadInstalledConfigsInternal()
    }
    
    /**
     * Carica le configurazioni delle sorgenti installate (metodo interno)
     */
    private fun loadInstalledConfigsInternal(): List<InstalledSourceConfig> {
        val configFile = getConfigFile()
        if (!configFile.exists()) {
            return emptyList()
        }
        
        return try {
            val json = configFile.readText()
            val type = object : com.google.gson.reflect.TypeToken<List<InstalledSourceConfig>>() {}.type
            gson.fromJson<List<InstalledSourceConfig>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("SourceManager", "Errore nel caricamento configurazioni", e)
            emptyList()
        }
    }
    
    /**
     * Salva le configurazioni delle sorgenti installate
     */
    private suspend fun saveInstalledConfigs(configs: List<InstalledSourceConfig>) = withContext(Dispatchers.IO) {
        try {
            val configFile = getConfigFile()
            val json = gson.toJson(configs)
            configFile.writeText(json)
        } catch (e: Exception) {
            android.util.Log.e("SourceManager", "Errore nel salvataggio configurazioni", e)
            throw e
        }
    }
    
    /**
     * Registra una sorgente come installata
     */
    suspend fun registerSource(sourceId: String, version: String) = withContext(Dispatchers.IO) {
        val configs = loadInstalledConfigsInternal().toMutableList()
        
        // Rimuovi eventuale configurazione esistente
        configs.removeAll { it.sourceId == sourceId }
        
        // Aggiungi nuova configurazione
        configs.add(
            InstalledSourceConfig(
                sourceId = sourceId,
                version = version,
                installDate = System.currentTimeMillis(),
                isEnabled = true
            )
        )
        
        saveInstalledConfigs(configs)
    }
    
    /**
     * Rimuove una sorgente installata
     */
    suspend fun uninstallSource(sourceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceDir = getSourceDirectory(sourceId)
            if (sourceDir.exists()) {
                sourceDir.deleteRecursively()
            }
            
            // Rimuovi dalla configurazione
            val configs = loadInstalledConfigsInternal().toMutableList()
            configs.removeAll { it.sourceId == sourceId }
            saveInstalledConfigs(configs)
            
            true
        } catch (e: Exception) {
            android.util.Log.e("SourceManager", "Errore nella disinstallazione sorgente $sourceId", e)
            false
        }
    }
    
    /**
     * Abilita/disabilita una sorgente
     */
    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val configs = loadInstalledConfigsInternal().toMutableList()
        val config = configs.find { it.sourceId == sourceId }
        if (config != null) {
            configs.remove(config)
            configs.add(config.copy(isEnabled = enabled))
            saveInstalledConfigs(configs)
        }
    }
    
    /**
     * Verifica se ci sono sorgenti installate
     */
    suspend fun hasInstalledSources(): Boolean = withContext(Dispatchers.IO) {
        val sources = getInstalledSources()
        sources.isNotEmpty()
    }
    
    /**
     * Verifica se ci sono sorgenti abilitate
     */
    suspend fun hasEnabledSources(): Boolean = withContext(Dispatchers.IO) {
        val enabledSources = getEnabledSources()
        enabledSources.isNotEmpty()
    }
    
    /**
     * Ottiene le sorgenti abilitate
     */
    suspend fun getEnabledSources(): List<Source> = withContext(Dispatchers.IO) {
        val sources = getInstalledSources()
        val configs = loadInstalledConfigsInternal()
        sources.filter { source ->
            val config = configs.find { it.sourceId == source.id }
            config?.isEnabled != false
        }
    }
    
    /**
     * Confronta due versioni e restituisce true se newVersion è più recente di oldVersion
     * Supporta versioni nel formato semver (es. "1.0.0", "1.2.3")
     */
    fun isVersionNewer(newVersion: String, oldVersion: String): Boolean {
        val newParts = newVersion.split(".").mapNotNull { it.toIntOrNull() }
        val oldParts = oldVersion.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(newParts.size, oldParts.size)
        
        for (i in 0 until maxLength) {
            val newPart = newParts.getOrElse(i) { 0 }
            val oldPart = oldParts.getOrElse(i) { 0 }
            
            when {
                newPart > oldPart -> return true
                newPart < oldPart -> return false
                // Continua al prossimo livello se sono uguali
            }
        }
        
        return false // Le versioni sono uguali
    }
    
    /**
     * Verifica se una sorgente può essere aggiornata
     */
    suspend fun canUpdateSource(sourceId: String, newVersion: String): Boolean = withContext(Dispatchers.IO) {
        val currentMetadata = getSourceMetadata(sourceId)
        if (currentMetadata == null) {
            return@withContext false
        }
        
        isVersionNewer(newVersion, currentMetadata.version)
    }
}

