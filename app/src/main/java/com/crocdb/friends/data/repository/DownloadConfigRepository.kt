package com.crocdb.friends.data.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crocdb.friends.domain.model.DownloadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    @ApplicationContext private val context: Context
) {
    private companion object {
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val AUTO_EXTRACT = booleanPreferencesKey("auto_extract_archives")
        val DELETE_AFTER_EXTRACT = booleanPreferencesKey("delete_archive_after_extraction")
        val WIFI_ONLY = booleanPreferencesKey("use_wifi_only")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ENABLE_ES_DE_COMPATIBILITY = booleanPreferencesKey("enable_es_de_compatibility")
        val ES_DE_ROMS_PATH = stringPreferencesKey("es_de_roms_path")
    }

    /**
     * Flow di configurazione download
     */
    val downloadConfig: Flow<DownloadConfig> = context.dataStore.data.map { preferences ->
        DownloadConfig(
            downloadPath = preferences[DOWNLOAD_PATH] ?: getDefaultDownloadPath(),
            autoExtractArchives = preferences[AUTO_EXTRACT] ?: true,
            deleteArchiveAfterExtraction = preferences[DELETE_AFTER_EXTRACT] ?: false,
            useWifiOnly = preferences[WIFI_ONLY] ?: false,
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: true,
            enableEsDeCompatibility = preferences[ENABLE_ES_DE_COMPATIBILITY] ?: false,
            esDeRomsPath = preferences[ES_DE_ROMS_PATH]
        )
    }

    /**
     * Aggiorna il path di download
     */
    suspend fun setDownloadPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_PATH] = path
        }
    }

    /**
     * Aggiorna estrazione automatica
     */
    suspend fun setAutoExtract(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_EXTRACT] = enabled
        }
    }

    /**
     * Aggiorna eliminazione archivi dopo estrazione
     */
    suspend fun setDeleteAfterExtract(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DELETE_AFTER_EXTRACT] = enabled
        }
    }

    /**
     * Aggiorna WiFi only
     */
    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_ONLY] = enabled
        }
    }

    /**
     * Aggiorna notifiche
     */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    /**
     * Aggiorna compatibilità ES-DE
     */
    suspend fun setEsDeCompatibility(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_ES_DE_COMPATIBILITY] = enabled
        }
    }

    /**
     * Aggiorna path cartella ROMs ES-DE
     */
    suspend fun setEsDeRomsPath(path: String?) {
        context.dataStore.edit { preferences ->
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
        val crocdbFolder = File(publicDownloads, "CrocdbFriends")
        
        if (!crocdbFolder.exists()) {
            crocdbFolder.mkdirs()
        }
        
        return crocdbFolder.absolutePath
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
}
