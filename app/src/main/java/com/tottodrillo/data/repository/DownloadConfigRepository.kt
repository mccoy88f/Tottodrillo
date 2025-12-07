package com.tottodrillo.data.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tottodrillo.domain.model.DownloadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "download_settings")

/**
 * Repository per gestire configurazioni download
 */
@Singleton
class DownloadConfigRepository @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private companion object {
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val AUTO_EXTRACT = booleanPreferencesKey("auto_extract_archives")
        val DELETE_AFTER_EXTRACT = booleanPreferencesKey("delete_archive_after_extraction")
        val WIFI_ONLY = booleanPreferencesKey("use_wifi_only")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ENABLE_ES_DE_COMPATIBILITY = booleanPreferencesKey("enable_es_de_compatibility")
        val ES_DE_ROMS_PATH = stringPreferencesKey("es_de_roms_path")
        val FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
        val ROM_INFO_SEARCH_PROVIDER = stringPreferencesKey("rom_info_search_provider")
    }

    /**
     * Flow di configurazione download
     */
    val downloadConfig: Flow<DownloadConfig> = appContext.dataStore.data.map { preferences ->
        DownloadConfig(
            downloadPath = preferences[DOWNLOAD_PATH] ?: getDefaultDownloadPath(),
            autoExtractArchives = preferences[AUTO_EXTRACT] ?: true,
            deleteArchiveAfterExtraction = preferences[DELETE_AFTER_EXTRACT] ?: false,
            useWifiOnly = preferences[WIFI_ONLY] ?: false,
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: true,
            enableEsDeCompatibility = preferences[ENABLE_ES_DE_COMPATIBILITY] ?: false,
            esDeRomsPath = preferences[ES_DE_ROMS_PATH],
            romInfoSearchProvider = preferences[ROM_INFO_SEARCH_PROVIDER] ?: "gamefaqs"
        )
    }

    /**
     * Aggiorna il path di download
     */
    suspend fun setDownloadPath(path: String) {
        appContext.dataStore.edit { preferences ->
            preferences[DOWNLOAD_PATH] = path
        }
    }

    /**
     * Aggiorna estrazione automatica
     */
    suspend fun setAutoExtract(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[AUTO_EXTRACT] = enabled
        }
    }

    /**
     * Aggiorna eliminazione archivi dopo estrazione
     */
    suspend fun setDeleteAfterExtract(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[DELETE_AFTER_EXTRACT] = enabled
        }
    }

    /**
     * Aggiorna WiFi only
     */
    suspend fun setWifiOnly(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[WIFI_ONLY] = enabled
        }
    }

    /**
     * Aggiorna notifiche
     */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    /**
     * Aggiorna compatibilità ES-DE
     */
    suspend fun setEsDeCompatibility(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[ENABLE_ES_DE_COMPATIBILITY] = enabled
        }
    }

    /**
     * Aggiorna path cartella ROMs ES-DE
     */
    suspend fun setEsDeRomsPath(path: String?) {
        appContext.dataStore.edit { preferences ->
            if (path != null) {
                preferences[ES_DE_ROMS_PATH] = path
            } else {
                preferences.remove(ES_DE_ROMS_PATH)
            }
        }
    }

    /**
     * Path di download predefinito
     */
    fun getDefaultDownloadPath(): String {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val tottodrilloFolder = File(publicDownloads, "Tottodrillo")
        
        if (!tottodrilloFolder.exists()) {
            tottodrilloFolder.mkdirs()
        }
        
        return tottodrilloFolder.absolutePath
    }

    /**
     * Verifica se il path è valido e scrivibile
     */
    fun isPathValid(path: String): Boolean {
        return try {
            val dir = File(path)
            dir.exists() && dir.isDirectory && dir.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Crea directory se non esiste
     */
    fun ensureDirectoryExists(path: String): Boolean {
        return try {
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ottiene spazio disponibile in bytes
     */
    fun getAvailableSpace(path: String): Long {
        return try {
            val dir = File(path)
            dir.usableSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Verifica se il primo avvio è già stato completato
     */
    suspend fun isFirstLaunchCompleted(): Boolean {
        val preferences = appContext.dataStore.data.first()
        return preferences[FIRST_LAUNCH_COMPLETED] ?: false
    }

    /**
     * Imposta il flag che indica che il primo avvio è stato completato
     */
    suspend fun setFirstLaunchCompleted() {
        appContext.dataStore.edit { preferences ->
            preferences[FIRST_LAUNCH_COMPLETED] = true
        }
    }

    /**
     * Aggiorna il provider di ricerca info ROMs
     */
    suspend fun setRomInfoSearchProvider(provider: String) {
        appContext.dataStore.edit { preferences ->
            preferences[ROM_INFO_SEARCH_PROVIDER] = provider
        }
    }
    
    /**
     * Espone il context per usi esterni (es. verificare permessi)
     */
    val context: Context
        get() = appContext
}
