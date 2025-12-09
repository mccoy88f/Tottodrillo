package com.tottodrillo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Configurazione API di una sorgente (da file api_config.json)
 */
data class SourceApiConfig(
    @SerializedName("base_url")
    val baseUrl: String,
    
    @SerializedName("endpoints")
    val endpoints: Map<String, EndpointConfig>,
    
    @SerializedName("request_models")
    val requestModels: Map<String, RequestModelConfig>? = null,
    
    @SerializedName("response_models")
    val responseModels: Map<String, ResponseModelConfig>? = null
)

/**
 * Configurazione di un endpoint
 */
data class EndpointConfig(
    @SerializedName("method")
    val method: String, // GET, POST, etc.
    
    @SerializedName("path")
    val path: String,
    
    @SerializedName("query_params")
    val queryParams: List<QueryParamConfig>? = null,
    
    @SerializedName("body_model")
    val bodyModel: String? = null, // Nome del modello request
    
    @SerializedName("response_model")
    val responseModel: String // Nome del modello response
)

/**
 * Configurazione di un parametro query
 */
data class QueryParamConfig(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("type")
    val type: String, // String, Int, List<String>, etc.
    
    @SerializedName("required")
    val required: Boolean = false,
    
    @SerializedName("default")
    val defaultValue: Any? = null
)

/**
 * Configurazione di un modello request
 */
data class RequestModelConfig(
    @SerializedName("fields")
    val fields: Map<String, FieldConfig>
)

/**
 * Configurazione di un modello response
 */
data class ResponseModelConfig(
    @SerializedName("wrapper")
    val wrapper: String? = null, // Nome del wrapper (es. "ApiResponse")
    
    @SerializedName("data_path")
    val dataPath: String? = null, // Path al dato (es. "data")
    
    @SerializedName("fields")
    val fields: Map<String, FieldConfig>
)

/**
 * Configurazione di un campo
 */
data class FieldConfig(
    @SerializedName("type")
    val type: String, // String, Int, Long, List<String>, etc.
    
    @SerializedName("serialized_name")
    val serializedName: String? = null,
    
    @SerializedName("nullable")
    val nullable: Boolean = false
)

