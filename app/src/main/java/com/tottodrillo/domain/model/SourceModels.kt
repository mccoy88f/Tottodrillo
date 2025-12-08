package com.tottodrillo.domain.model

/**
 * Modello per una sorgente installabile
 */
data class Source(
    val id: String, // Identificatore univoco (es. "crocdb", "screenscraper")
    val name: String, // Nome visualizzato
    val version: String, // Versione sorgente
    val description: String? = null, // Descrizione opzionale
    val author: String? = null, // Autore della sorgente
    val baseUrl: String, // URL base dell'API
    val isInstalled: Boolean = false, // Se è installata
    val installPath: String? = null, // Percorso di installazione
    val type: String = "api" // Tipo sorgente: "api", "java", "python"
)

/**
 * Metadata di una sorgente dal file source.json
 */
data class SourceMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val author: String? = null,
    val baseUrl: String? = null, // Opzionale per sorgenti non-API
    val minAppVersion: String? = null, // Versione minima app richiesta
    val apiPackage: String? = null, // Package Java/Kotlin per le classi API (opzionale)
    val type: String = "api", // Tipo sorgente: "api", "java", "python"
    val mainClass: String? = null, // Classe principale per sorgenti Java (es. "com.example.MySource")
    val pythonScript: String? = null, // Script Python principale per sorgenti Python (es. "main.py")
    val dependencies: List<String>? = null, // Dipendenze per sorgenti Java (JAR files) o Python (requirements.txt)
    val imageRefererPattern: String? = null, // Pattern per costruire il Referer header per le immagini (es. "https://vimm.net/vault/{id}")
    val defaultImage: String? = null, // URL dell'immagine placeholder da usare quando una ROM non ha immagini
    val downloadInterceptPatterns: List<String>? = null // Pattern per intercettare download nel WebView (es. ["download.nswpediax.site", ".nsp", ".xci"])
)

/**
 * Configurazione di una sorgente installata
 */
data class InstalledSourceConfig(
    val sourceId: String,
    val version: String,
    val installDate: Long,
    val isEnabled: Boolean = true
)

/**
 * Informazioni su una versione disponibile di una sorgente dal file sources-versions.json
 */
data class SourceVersionInfo(
    val id: String,
    val version: String,
    val downloadUrl: String,
    val changelog: String? = null,
    val minAppVersion: String? = null,
    val releaseDate: String? = null
)

/**
 * Risposta del file sources-versions.json
 */
data class SourcesVersionsResponse(
    val sources: List<SourceVersionInfo>
)

/**
 * Informazioni su un aggiornamento disponibile per una sorgente
 */
data class SourceUpdate(
    val sourceId: String,
    val sourceName: String,
    val currentVersion: String,
    val availableVersion: String,
    val downloadUrl: String,
    val changelog: String? = null,
    val minAppVersion: String? = null
)

