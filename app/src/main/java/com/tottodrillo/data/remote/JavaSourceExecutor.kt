package com.tottodrillo.data.remote

import com.tottodrillo.data.model.EntryResponse
import com.tottodrillo.data.model.RegionsResponse
import com.tottodrillo.data.model.SearchResults
import com.tottodrillo.domain.model.SourceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLClassLoader
import java.net.URL

/**
 * Executor per sorgenti Java/Kotlin
 * Carica dinamicamente classi da JAR files inclusi nel pacchetto sorgente
 */
class JavaSourceExecutor(
    private val metadata: SourceMetadata,
    private val sourceDir: File,
    private val executorInstance: Any // Istanza della classe implementata dallo sviluppatore
) : SourceExecutor {
    
    private val searchMethod = executorInstance.javaClass.getMethod(
        "searchRoms",
        String::class.java,
        List::class.java,
        List::class.java,
        Int::class.java,
        Int::class.java
    )
    
    private val getEntryMethod = executorInstance.javaClass.getMethod(
        "getEntry",
        String::class.java
    )
    
    private val getPlatformsMethod = executorInstance.javaClass.getMethod("getPlatforms")
    private val getRegionsMethod = executorInstance.javaClass.getMethod("getRegions")
    
    override suspend fun searchRoms(
        searchKey: String?,
        platforms: List<String>,
        regions: List<String>,
        maxResults: Int,
        page: Int
    ): Result<SearchResults> = withContext(Dispatchers.IO) {
        try {
            val result = searchMethod.invoke(
                executorInstance,
                searchKey,
                platforms,
                regions,
                maxResults,
                page
            ) as? SearchResults
            
            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(IllegalStateException("searchRoms ha ritornato null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getEntry(slug: String, includeDownloadLinks: Boolean): Result<EntryResponse> = withContext(Dispatchers.IO) {
        try {
            val result = getEntryMethod.invoke(executorInstance, slug) as? EntryResponse
            
            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(IllegalStateException("getEntry ha ritornato null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getPlatforms(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val result = getPlatformsMethod.invoke(executorInstance) as? Map<*, *>
            
            if (result != null) {
                @Suppress("UNCHECKED_CAST")
                Result.success(result as Map<String, Any>)
            } else {
                Result.failure(IllegalStateException("getPlatforms ha ritornato null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getRegions(): Result<RegionsResponse> = withContext(Dispatchers.IO) {
        try {
            val result = getRegionsMethod.invoke(executorInstance) as? RegionsResponse
            
            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(IllegalStateException("getRegions ha ritornato null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    companion object {
        /**
         * Crea un JavaSourceExecutor caricando dinamicamente la classe dalla sorgente
         */
        fun create(metadata: SourceMetadata, sourceDir: File): JavaSourceExecutor {
            val mainClass = metadata.mainClass
                ?: throw IllegalArgumentException("mainClass è richiesto per sorgenti Java")
            
            // Carica i JAR files dalla cartella libs/ o dalla root
            val jarFiles = mutableListOf<File>()
            
            // Cerca JAR nella cartella libs/
            val libsDir = File(sourceDir, "libs")
            if (libsDir.exists() && libsDir.isDirectory) {
                libsDir.listFiles()?.filter { it.extension == "jar" }?.forEach {
                    jarFiles.add(it)
                }
            }
            
            // Cerca JAR nella root
            sourceDir.listFiles()?.filter { it.extension == "jar" }?.forEach {
                jarFiles.add(it)
            }
            
            // Crea URLClassLoader con tutti i JAR
            val urls = jarFiles.map { it.toURI().toURL() }.toTypedArray()
            val classLoader = URLClassLoader(urls, JavaSourceExecutor::class.java.classLoader)
            
            // Carica la classe principale
            val clazz = classLoader.loadClass(mainClass)
            
            // Cerca un costruttore che accetta (SourceMetadata, File)
            val constructor = try {
                clazz.getConstructor(SourceMetadata::class.java, File::class.java)
            } catch (e: NoSuchMethodException) {
                // Prova con costruttore senza parametri
                clazz.getConstructor()
            }
            
            // Crea istanza
            val instance = if (constructor.parameterCount == 2) {
                constructor.newInstance(metadata, sourceDir)
            } else {
                constructor.newInstance()
            }
            
            return JavaSourceExecutor(metadata, sourceDir, instance)
        }
    }
}

