package com.tottodrillo.domain.manager

import android.content.Context
import com.google.gson.Gson
import com.tottodrillo.domain.model.SourceMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installer per installare sorgenti da file ZIP
 */
@Singleton
class SourceInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager
) {
    private val gson = Gson()
    
    companion object {
        private const val METADATA_FILE = "source.json"
        private const val API_CONFIG_FILE = "api_config.json"
        private const val MAPPING_FILE = "mapping.json" // Opzionale
    }
    
    /**
     * Installa una sorgente da un file ZIP
     */
    suspend fun installFromZip(zipFile: File): Result<SourceMetadata> = withContext(Dispatchers.IO) {
        try {
            // Estrai il ZIP
            val tempDir = File(context.cacheDir, "source_install_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                extractZip(zipFile, tempDir)
                
                // Valida la struttura
                val metadataFile = File(tempDir, METADATA_FILE)
                if (!metadataFile.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("File $METADATA_FILE non trovato nello ZIP")
                    )
                }
                
                // Carica metadata
                val metadata = gson.fromJson(metadataFile.readText(), SourceMetadata::class.java)
                
                // Valida metadata
                validateMetadata(metadata)
                
                // Valida struttura file
                validateSourceStructure(tempDir, metadata)
                
                // Verifica se la sorgente è già installata
                val isAlreadyInstalled = sourceManager.isSourceInstalled(metadata.id)
                val isUpdate = if (isAlreadyInstalled) {
                    val currentMetadata = sourceManager.getSourceMetadata(metadata.id)
                    if (currentMetadata != null) {
                        sourceManager.isVersionNewer(metadata.version, currentMetadata.version)
                    } else {
                        false
                    }
                } else {
                    false
                }
                
                // Sposta nella directory delle sorgenti
                val targetDir = File(context.filesDir, "sources/${metadata.id}")
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                targetDir.mkdirs()
                
                tempDir.copyRecursively(targetDir, overwrite = true)
                
                // Registra la sorgente (mantiene lo stato enabled se era già installata)
                if (isUpdate && isAlreadyInstalled) {
                    val configs = sourceManager.loadInstalledConfigs()
                    val existingConfig = configs.find { it.sourceId == metadata.id }
                    val wasEnabled = existingConfig?.isEnabled ?: true
                    
                    sourceManager.registerSource(metadata.id, metadata.version)
                    // Ripristina lo stato enabled/disabled
                    sourceManager.setSourceEnabled(metadata.id, wasEnabled)
                } else {
                    sourceManager.registerSource(metadata.id, metadata.version)
                }
                
                Result.success(metadata)
            } finally {
                // Pulisci directory temporanea
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            android.util.Log.e("SourceInstaller", "Errore nell'installazione sorgente", e)
            Result.failure(e)
        }
    }
    
    /**
     * Estrae un file ZIP
     */
    private fun extractZip(zipFile: File, targetDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zis.copyTo(it) }
                }
                
                entry = zis.nextEntry
            }
        }
    }
    
    /**
     * Valida i metadata della sorgente
     */
    private fun validateMetadata(metadata: SourceMetadata) {
        require(metadata.id.isNotBlank()) { "ID sorgente non può essere vuoto" }
        require(metadata.name.isNotBlank()) { "Nome sorgente non può essere vuoto" }
        require(metadata.version.isNotBlank()) { "Versione sorgente non può essere vuota" }
        
        // Retrocompatibilità: se type è null o vuoto, assume "api" (comportamento predefinito)
        val sourceType = (metadata.type.takeIf { !it.isNullOrBlank() } ?: "api").lowercase()
        
        // Valida campi specifici per tipo
        when (sourceType) {
            "api" -> {
                require(!metadata.baseUrl.isNullOrBlank()) {
                    "baseUrl è obbligatorio per sorgenti API"
                }
                // Valida formato URL
                require(android.util.Patterns.WEB_URL.matcher(metadata.baseUrl!!).matches()) {
                    "Base URL non valido: ${metadata.baseUrl}"
                }
            }
            "java", "kotlin" -> {
                require(!metadata.mainClass.isNullOrBlank()) {
                    "mainClass è obbligatorio per sorgenti Java/Kotlin"
                }
            }
            "python" -> {
                require(!metadata.pythonScript.isNullOrBlank()) {
                    "pythonScript è obbligatorio per sorgenti Python"
                }
            }
            else -> {
                throw IllegalArgumentException("Tipo sorgente non supportato: ${metadata.type}. Usa 'api', 'java', o 'python'")
            }
        }
    }
    
    /**
     * Valida la struttura della sorgente
     */
    private fun validateSourceStructure(sourceDir: File, metadata: SourceMetadata) {
        val sourceType = metadata.type.lowercase()
        
        when (sourceType) {
            "api" -> {
                // Per sorgenti API, api_config.json è obbligatorio
                val apiConfigFile = File(sourceDir, API_CONFIG_FILE)
                if (!apiConfigFile.exists()) {
                    throw IllegalArgumentException("File $API_CONFIG_FILE non trovato (obbligatorio per sorgenti API)")
                }
                
                // Valida che api_config.json sia JSON valido
                try {
                    val apiConfig = apiConfigFile.readText()
                    gson.fromJson(apiConfig, com.tottodrillo.data.model.SourceApiConfig::class.java)
                } catch (e: Exception) {
                    throw IllegalArgumentException("File $API_CONFIG_FILE non valido: ${e.message}")
                }
            }
            "java", "kotlin" -> {
                // Per sorgenti Java, verifica che la classe principale esista
                // (verrà verificato al caricamento)
                val mainClass = metadata.mainClass
                if (mainClass.isNullOrBlank()) {
                    throw IllegalArgumentException("mainClass è obbligatorio per sorgenti Java/Kotlin")
                }
            }
            "python" -> {
                // Per sorgenti Python, verifica che lo script esista
                val pythonScript = metadata.pythonScript
                if (pythonScript.isNullOrBlank()) {
                    throw IllegalArgumentException("pythonScript è obbligatorio per sorgenti Python")
                }
                
                val scriptFile = File(sourceDir, pythonScript)
                if (!scriptFile.exists()) {
                    throw IllegalArgumentException("Script Python non trovato: $pythonScript")
                }
            }
        }
    }
    
    /**
     * Verifica se un file ZIP è una sorgente valida (senza installarla)
     */
    suspend fun validateZip(zipFile: File): Result<SourceMetadata> = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "source_validate_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                extractZip(zipFile, tempDir)
                
                val metadataFile = File(tempDir, METADATA_FILE)
                if (!metadataFile.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("File $METADATA_FILE non trovato nello ZIP")
                    )
                }
                
                val metadata = gson.fromJson(metadataFile.readText(), SourceMetadata::class.java)
                validateMetadata(metadata)
                validateSourceStructure(tempDir, metadata)
                
                Result.success(metadata)
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

