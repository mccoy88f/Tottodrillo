package com.tottodrillo.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tottodrillo.domain.model.SearchFilters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/**
 * Modello per una ricerca salvata nella cronologia
 */
data class SavedSearch(
    val query: String,
    val filters: SearchFilters,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Ottiene una descrizione testuale dei filtri attivi
     */
    fun getFiltersDescription(): String {
        val parts = mutableListOf<String>()
        
        if (filters.selectedPlatforms.isNotEmpty()) {
            parts.add("${filters.selectedPlatforms.size} piattaforma${if (filters.selectedPlatforms.size > 1) "e" else ""}")
        }
        if (filters.selectedRegions.isNotEmpty()) {
            parts.add("${filters.selectedRegions.size} regione${if (filters.selectedRegions.size > 1) "i" else ""}")
        }
        if (filters.selectedSources.isNotEmpty()) {
            parts.add("${filters.selectedSources.size} sorgente${if (filters.selectedSources.size > 1) "i" else ""}")
        }
        if (filters.selectedFormats.isNotEmpty()) {
            parts.add("${filters.selectedFormats.size} formato${if (filters.selectedFormats.size > 1) "i" else ""}")
        }
        
        return if (parts.isNotEmpty()) {
            " • ${parts.joinToString(", ")}"
        } else {
            ""
        }
    }
}

/**
 * Repository per gestire la cronologia delle ricerche
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gson: Gson
) {
    private companion object {
        val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history_list")
        private const val MAX_HISTORY_SIZE = 10
    }

    /**
     * Flow della cronologia ricerche (ultime 10)
     */
    val searchHistory: Flow<List<SavedSearch>> = appContext.searchHistoryDataStore.data.map { preferences ->
        val historyJson = preferences[SEARCH_HISTORY_KEY]
        if (historyJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<SavedSearch>>() {}.type
                gson.fromJson<List<SavedSearch>>(historyJson, type) ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("SearchHistoryRepository", "Errore nel parsing cronologia ricerche", e)
                emptyList()
            }
        }
    }

    /**
     * Salva una ricerca nella cronologia
     * Rimuove duplicati e mantiene solo le ultime MAX_HISTORY_SIZE ricerche
     */
    suspend fun saveSearch(query: String, filters: SearchFilters) {
        // Salva solo se c'è una query o almeno un filtro attivo
        if (query.isBlank() && !filters.hasActiveFilters()) {
            android.util.Log.d("SearchHistoryRepository", "⚠️ Salvataggio ricerca saltato: query vuota e nessun filtro attivo")
            return
        }

        android.util.Log.d("SearchHistoryRepository", "💾 Salvataggio ricerca: query='$query', filtri=${filters.selectedPlatforms.size} piattaforme, ${filters.selectedRegions.size} regioni")
        appContext.searchHistoryDataStore.edit { preferences ->
            val currentHistoryJson = preferences[SEARCH_HISTORY_KEY]
            val currentHistory = if (currentHistoryJson.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<SavedSearch>>() {}.type
                    gson.fromJson<List<SavedSearch>>(currentHistoryJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // Crea la nuova ricerca
            val newSearch = SavedSearch(
                query = query,
                filters = filters,
                timestamp = System.currentTimeMillis()
            )

            // Rimuovi duplicati (stessa query e stessi filtri)
            val filteredHistory = currentHistory.filterNot { existing ->
                existing.query == newSearch.query &&
                existing.filters.selectedPlatforms == newSearch.filters.selectedPlatforms &&
                existing.filters.selectedRegions == newSearch.filters.selectedRegions &&
                existing.filters.selectedSources == newSearch.filters.selectedSources &&
                existing.filters.selectedFormats == newSearch.filters.selectedFormats
            }

            // Aggiungi la nuova ricerca all'inizio
            val updatedHistory = (listOf(newSearch) + filteredHistory).take(MAX_HISTORY_SIZE)

            // Salva
            preferences[SEARCH_HISTORY_KEY] = gson.toJson(updatedHistory)
            android.util.Log.d("SearchHistoryRepository", "✅ Ricerca salvata. Totale ricerche nella cronologia: ${updatedHistory.size}")
        }
    }

    /**
     * Rimuove una ricerca dalla cronologia
     */
    suspend fun removeSearch(search: SavedSearch) {
        appContext.searchHistoryDataStore.edit { preferences ->
            val currentHistoryJson = preferences[SEARCH_HISTORY_KEY]
            if (currentHistoryJson.isNullOrBlank()) {
                return@edit
            }

            val currentHistory = try {
                val type = object : TypeToken<List<SavedSearch>>() {}.type
                gson.fromJson<List<SavedSearch>>(currentHistoryJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            // Rimuovi la ricerca
            val updatedHistory = currentHistory.filterNot { existing ->
                existing.query == search.query &&
                existing.filters.selectedPlatforms == search.filters.selectedPlatforms &&
                existing.filters.selectedRegions == search.filters.selectedRegions &&
                existing.filters.selectedSources == search.filters.selectedSources &&
                existing.filters.selectedFormats == search.filters.selectedFormats &&
                existing.timestamp == search.timestamp
            }

            preferences[SEARCH_HISTORY_KEY] = gson.toJson(updatedHistory)
        }
    }

    /**
     * Pulisce tutta la cronologia
     */
    suspend fun clearHistory() {
        appContext.searchHistoryDataStore.edit { preferences ->
            preferences.remove(SEARCH_HISTORY_KEY)
        }
    }
}

