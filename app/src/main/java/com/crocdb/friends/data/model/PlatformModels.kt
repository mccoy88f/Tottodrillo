package com.crocdb.friends.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modello per platforms_main.json
 */
data class PlatformsMainResponse(
    @SerializedName("platforms")
    val platforms: List<MotherPlatform>,
    @SerializedName("total")
    val total: Int
)

/**
 * Piattaforma madre dal file platforms_main.json
 */
data class MotherPlatform(
    @SerializedName("mother_code")
    val motherCode: String,
    @SerializedName("name")
    val name: String?,
    @SerializedName("image")
    val image: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("brand")
    val brand: String?
)

/**
 * Modello per crocdb.sourcesetting
 */
data class SourceSetting(
    @SerializedName("source_name")
    val sourceName: String,
    @SerializedName("sources")
    val sources: Map<String, SourceConfig>,
    @SerializedName("total_mappings")
    val totalMappings: Int
)

/**
 * Configurazione di una sorgente
 */
data class SourceConfig(
    @SerializedName("source")
    val source: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("base_url")
    val baseUrl: String,
    @SerializedName("mapping")
    val mapping: Map<String, Any> // Può essere String o List<String>
)

