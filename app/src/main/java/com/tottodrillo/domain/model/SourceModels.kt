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
    val installPath: String? = null // Percorso di installazione
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
    val dependencies: List<String>? = null // Dipendenze per sorgenti Java (JAR files) o Python (requirements.txt)
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

