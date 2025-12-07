package com.tottodrillo.data.cache

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.tottodrillo.data.repository.RomCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

/**
 * Fetcher personalizzato per Coil che gestisce la cache delle immagini
 * Controlla prima la cache locale, poi scarica e salva in cache se necessario
 */
class CachedImageFetcher(
    private val data: String,
    private val options: Options,
    private val cacheManager: RomCacheManager,
    private val callFactory: Call.Factory
) : Fetcher {
    
    override suspend fun fetch(): FetchResult? {
        return withContext(Dispatchers.IO) {
            // Se è un file:// URI, usa direttamente
            if (data.startsWith("file://")) {
                val file = File(data.removePrefix("file://"))
                if (file.exists()) {
                    val okioPath = file.absolutePath.toPath()
                    return@withContext SourceResult(
                        source = ImageSource(okioPath, fileSystem = FileSystem.SYSTEM),
                        mimeType = null,
                        dataSource = DataSource.DISK
                    )
                }
            }
            
            // Controlla se l'immagine è in cache (solo per URL HTTP/HTTPS)
            if (data.startsWith("http://") || data.startsWith("https://")) {
                val cachedPath = cacheManager.getCachedImagePath(data)
                if (cachedPath != null) {
                    val cachedFile = File(cachedPath.removePrefix("file://"))
                    if (cachedFile.exists()) {
                        android.util.Log.d("CachedImageFetcher", "✅ Immagine caricata da cache: $data")
                        val okioPath = cachedFile.absolutePath.toPath()
                        return@withContext SourceResult(
                            source = ImageSource(okioPath, fileSystem = FileSystem.SYSTEM),
                            mimeType = null,
                            dataSource = DataSource.DISK
                        )
                    }
                }
            }
            
            // Se non è in cache, scarica e salva
            try {
                val httpUrl = data.toHttpUrl()
                val request = Request.Builder()
                    .url(httpUrl)
                    .build()
                
                val response = callFactory.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext null
                }
                
                val body = response.body ?: return@withContext null
                val imageBytes = body.bytes()
                
                // Salva in cache
                val savedPath = cacheManager.saveImageToCache(data, imageBytes)
                if (savedPath != null) {
                    android.util.Log.d("CachedImageFetcher", "✅ Immagine salvata in cache: $data")
                    val cachedFile = File(savedPath.removePrefix("file://"))
                    val okioPath = cachedFile.absolutePath.toPath()
                    return@withContext SourceResult(
                        source = ImageSource(okioPath, fileSystem = FileSystem.SYSTEM),
                        mimeType = body.contentType()?.toString(),
                        dataSource = DataSource.NETWORK
                    )
                } else {
                    // Se il salvataggio fallisce, salva temporaneamente e usa il file
                    val tempFile = File.createTempFile("coil_", ".tmp", options.context.cacheDir)
                    tempFile.outputStream().use { it.write(imageBytes) }
                    val okioPath = tempFile.absolutePath.toPath()
                    return@withContext SourceResult(
                        source = ImageSource(okioPath, fileSystem = FileSystem.SYSTEM),
                        mimeType = body.contentType()?.toString(),
                        dataSource = DataSource.NETWORK
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("CachedImageFetcher", "❌ Errore caricamento immagine: $data", e)
                null
            }
        }
    }
    
    class Factory(
        private val cacheManager: RomCacheManager,
        private val callFactory: Call.Factory
    ) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data !is String) return null
            // Gestisce solo URL HTTP/HTTPS e file://
            if (!data.startsWith("http://") && 
                !data.startsWith("https://") && 
                !data.startsWith("file://")) {
                return null
            }
            return CachedImageFetcher(data, options, cacheManager, callFactory)
        }
    }
}

