package com.tottodrillo.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Worker per gestire download di ROM in background
 */
class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_URL = "download_url"
        const val KEY_ORIGINAL_URL = "original_url" // URL originale del link (se diverso dall'URL finale)
        const val KEY_INTERMEDIATE_URL = "intermediate_url" // URL pagina intermedia da visitare per cookie (es. NSWpedia)
        const val KEY_DELAY_SECONDS = "delay_seconds" // Secondi da attendere prima del download
        const val KEY_COOKIES = "cookies" // Cookie dal WebView per mantenere la sessione (es. Cloudflare)
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TARGET_PATH = "target_path"
        const val KEY_ROM_TITLE = "rom_title"
        const val KEY_TASK_ID = "task_id"
        
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_MAX = "progress_max"
        const val PROGRESS_PERCENTAGE = "progress_percentage"
        
        private const val NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
        
        private const val BUFFER_SIZE = 8192
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // CookieJar in memoria per gestire i cookie di sessione
    private val cookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.addAll(cookies)
        }
        
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookies.filter { it.matches(url) }
        }
    }

    // Configura SSL per accettare certificati (necessario per Vimm's Lair)
    // Nota: In produzione, dovresti usare certificati validi
    private val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
        object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    )
    
    private val sslContext = javax.net.ssl.SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }
    
    private val sslSocketFactory = sslContext.socketFactory

    // Client HTTP dedicato per il worker con CookieJar per gestire i cookie di sessione
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
        .hostnameVerifier { _, _ -> true } // Accetta tutti gli hostname
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val originalUrl = inputData.getString(KEY_ORIGINAL_URL) // URL originale (opzionale, per download da WebView)
        val intermediateUrl = inputData.getString(KEY_INTERMEDIATE_URL) // URL pagina intermedia (opzionale, per cookie)
        val delaySeconds = inputData.getInt(KEY_DELAY_SECONDS, 0) // Secondi da attendere (0 se non presente)
        val cookies = inputData.getString(KEY_COOKIES) // Cookie dal WebView (opzionale, per mantenere sessione)
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val targetPath = inputData.getString(KEY_TARGET_PATH) ?: return@withContext Result.failure()
        val romTitle = inputData.getString(KEY_ROM_TITLE) ?: "ROM"
        val romSlug = inputData.getString("rom_slug")
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()

        createNotificationChannel()

        try {
            // Set foreground service
            setForeground(createForegroundInfo(romTitle, 0, romSlug, id))

            // Esegui download
            val outputFile = File(targetPath, fileName)
            
            // Sovrascrivi sempre il file se esiste già
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            downloadFile(url, outputFile, romTitle, romSlug, intermediateUrl, delaySeconds, cookies)

            // Crea/aggiorna file .status per confermare il download completato
            // Formato multi-riga:
            // Prima riga (opzionale): SLUG:<slug>
            // Righe successive: <URL> o <URL>\t<PATH_ESTRAZIONE>
            try {
                val statusFile = File(targetPath, "$fileName.status")
                
                // Leggi le righe esistenti (se il file esiste)
                val existingLines = if (statusFile.exists()) {
                    statusFile.readLines().filter { it.isNotBlank() }
                } else {
                    emptyList()
                }
                
                // Estrai lo slug esistente (se presente) e le righe URL
                val existingSlug = existingLines.firstOrNull { it.startsWith("SLUG:") }?.substringAfter("SLUG:")?.trim()
                val urlLines = existingLines.filterNot { it.startsWith("SLUG:") }
                
                // Usa lo slug passato o quello esistente
                val slugToSave = romSlug?.takeIf { it.isNotEmpty() } ?: existingSlug
                
                // Verifica se l'URL (o l'URL originale) esiste già nelle righe
                val urlExists = urlLines.any { line ->
                    val existingUrl = if (line.contains('\t')) {
                        line.substringBefore('\t')
                    } else {
                        line.trim()
                    }
                    existingUrl == url || (originalUrl != null && existingUrl == originalUrl)
                }
                
                val updatedUrlLines = urlLines.toMutableList()
                if (!urlExists) {
                    // Aggiungi una nuova riga per questo URL
                    updatedUrlLines.add(url)
                    
                    // Se c'è un URL originale diverso, aggiungilo anche al file .status
                    // Questo permette di trovare il download anche quando si cerca con l'URL originale
                    if (originalUrl != null && originalUrl != url) {
                        val originalUrlExists = urlLines.any { line ->
                            val existingUrl = if (line.contains('\t')) {
                                line.substringBefore('\t')
                            } else {
                                line.trim()
                            }
                            existingUrl == originalUrl
                        }
                        
                        if (!originalUrlExists) {
                            updatedUrlLines.add(originalUrl)
                        }
                    }
                } else {
                }
                
                // Costruisci il contenuto finale: slug (se presente) seguito dalle righe URL
                val finalContent = buildString {
                    if (slugToSave != null) {
                        appendLine("SLUG:$slugToSave")
                    }
                    updatedUrlLines.forEach { line ->
                        appendLine(line)
                    }
                }.trimEnd()
                
                statusFile.writeText(finalContent)
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Errore nella creazione/aggiornamento del file .status", e)
            }

            // Success
            showCompletedNotification(romTitle, outputFile.absolutePath, romSlug)
            
            Result.success(workDataOf(
                "file_path" to outputFile.absolutePath,
                "file_size" to outputFile.length()
            ))
        } catch (e: Exception) {
            showErrorNotification(romTitle, e.message ?: "Errore sconosciuto", romSlug)
            Result.failure(workDataOf("error" to e.message))
        }
    }

    /**
     * Scarica il file e aggiorna il progresso
     */
    private suspend fun downloadFile(
        url: String, 
        outputFile: File, 
        romTitle: String, 
        romSlug: String?,
        intermediateUrl: String? = null,
        delaySeconds: Int = 0,
        cookies: String? = null // Cookie dal WebView per mantenere la sessione
    ) {
        Log.d("DownloadWorker", "Avvio download: $url")
        
        val requestBuilder = Request.Builder()
        
        // Se abbiamo cookie dal WebView, usali direttamente (priorità più alta)
        if (cookies != null && cookies.isNotEmpty()) {
            requestBuilder
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
            
            // Aggiungi Referer se è un link NSWpedia
            // Usa intermediateUrl come Referer se disponibile (più accurato), altrimenti usa il dominio principale
            if (url.contains("nswpedia") || url.contains("download.nswpediax.site")) {
                val refererUrl = if (intermediateUrl != null && intermediateUrl.isNotEmpty()) {
                    intermediateUrl
                } else {
                    "https://nswpedia.com/"
                }
                requestBuilder.header("Referer", refererUrl)
            }
        }
        
        // Per link con intermediateUrl (es. NSWpedia link diretti), visita la pagina intermedia per ottenere cookie
        // NOTA: Il delay è già stato gestito nel ViewModel con countdown visibile, qui visitiamo solo per i cookie
        if (intermediateUrl != null) {
            Log.d("DownloadWorker", "🔧 Rilevato intermediateUrl, visito pagina intermedia per cookie: $intermediateUrl")
            try {
                val intermediateRequest = Request.Builder()
                    .url(intermediateUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Referer", "https://nswpedia.com/")
                    .build()
                
                okHttpClient.newCall(intermediateRequest).execute().use { intermediateResponse ->
                    if (intermediateResponse.isSuccessful) {
                        // I cookie vengono salvati automaticamente dal CookieJar
                    } else {
                        Log.w("DownloadWorker", "⚠️ Impossibile visitare pagina intermedia: ${intermediateResponse.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w("DownloadWorker", "⚠️ Errore nel visitare pagina intermedia: ${e.message}")
            }
        }
        
        // Per Vimm's Lair, visita prima la pagina ROM per ottenere i cookie di sessione
        if (url.contains("vimm.net") && url.contains("mediaId")) {
            Log.d("DownloadWorker", "🔧 Rilevato URL Vimm's Lair, visito pagina ROM per cookie")
            
            // Estrai mediaId e alt dall'URL
            val mediaId = url.substringAfter("mediaId=").substringBefore("&")
            val alt = if (url.contains("alt=")) url.substringAfter("alt=").substringBefore("&") else null
            
            // Costruisci l'URL della pagina ROM
            val romPageUrl = if (romSlug != null && romSlug.isNotEmpty()) {
                "https://vimm.net/vault/$romSlug"
            } else {
                // Se non abbiamo lo slug, prova a estrarre l'ID dall'URL originale
                // Questo è un fallback, idealmente dovremmo sempre avere lo slug
                "https://vimm.net/vault/"
            }
            
            // Visita la pagina ROM per ottenere i cookie di sessione
            val cookieStore = okHttpClient.cookieJar
            try {
                val pageRequest = Request.Builder()
                    .url(romPageUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                
                okHttpClient.newCall(pageRequest).execute().use { pageResponse ->
                    if (pageResponse.isSuccessful) {
                        // I cookie vengono salvati automaticamente dal CookieJar
                    } else {
                        Log.w("DownloadWorker", "⚠️ Impossibile visitare pagina ROM: ${pageResponse.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w("DownloadWorker", "⚠️ Errore nel visitare pagina ROM: ${e.message}")
            }
            
            // Costruisci l'URL di download nel formato corretto
            // Il dominio può essere dl2 o dl3 a seconda della ROM (estratto dal form)
            val downloadUrl = if (url.contains("dl2.vimm.net") || url.contains("dl3.vimm.net")) {
                // URL già nel formato corretto
                url
            } else {
                // Usa dl2 come default (più comune), l'URL dovrebbe già contenere il dominio corretto
                // Se non presente, prova dl2
                val baseUrl = "https://dl2.vimm.net/?mediaId=$mediaId"
                if (alt != null) {
                    "$baseUrl&alt=$alt"
                } else {
                    baseUrl
                }
            }
            
            requestBuilder
                .url(downloadUrl)
                .header("Referer", romPageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            // I cookie dal WebView sono già stati aggiunti sopra se presenti
        } else {
            requestBuilder.url(url)
            // Se non abbiamo cookie dal WebView, aggiungi header di default
            if (cookies == null || cookies.isEmpty()) {
                requestBuilder
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                
                // Aggiungi Referer anche se non abbiamo cookie (potrebbe essere necessario)
                if (url.contains("nswpedia") || url.contains("download.nswpediax.site")) {
                    val refererUrl = if (intermediateUrl != null && intermediateUrl.isNotEmpty()) {
                        intermediateUrl
                    } else {
                        "https://nswpedia.com/"
                    }
                    requestBuilder.header("Referer", refererUrl)
                }
            }
            // I cookie dal WebView sono già stati aggiunti sopra se presenti
        }
        
        val request = requestBuilder.build()

        okHttpClient.newCall(request).execute().use { response ->
            Log.d("DownloadWorker", "Risposta: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()?.take(200)
                Log.e("DownloadWorker", "❌ Download fallito: ${response.code} - $errorBody")
                throw Exception("Download fallito: ${response.code} ${response.message}")
            }

            val body = response.body ?: throw Exception("Response body vuoto")
            val contentLength = body.contentLength()
            
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    downloadWithProgress(input, output, contentLength, romTitle, romSlug)
                }
            }
            
        }
    }

    /**
     * Scarica con aggiornamento progressivo
     */
    private suspend fun downloadWithProgress(
        input: InputStream,
        output: FileOutputStream,
        totalBytes: Long,
        romTitle: String,
        romSlug: String?
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalBytesRead = 0L
        var lastNotificationTime = System.currentTimeMillis()

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            // Aggiorna progresso ogni 500ms
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNotificationTime > 500) {
                val progress = if (totalBytes > 0) {
                    ((totalBytesRead.toFloat() / totalBytes) * 100).toInt()
                } else 0

                setProgress(workDataOf(
                    PROGRESS_CURRENT to totalBytesRead,
                    PROGRESS_MAX to totalBytes,
                    PROGRESS_PERCENTAGE to progress
                ))

                setForeground(createForegroundInfo(romTitle, progress, romSlug, id))
                lastNotificationTime = currentTime
            }
        }
    }

    /**
     * Crea notification channel per Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download ROM in background"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea ForegroundInfo per il servizio
     */
    private fun createForegroundInfo(romTitle: String, progress: Int, romSlug: String? = null, workId: java.util.UUID = id): ForegroundInfo {
        // Crea Intent per l'azione "Interrompi download"
        val cancelIntent = Intent(appContext, com.tottodrillo.data.receiver.DownloadCancelReceiver::class.java).apply {
            action = com.tottodrillo.data.receiver.DownloadCancelReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(com.tottodrillo.data.receiver.DownloadCancelReceiver.EXTRA_WORK_ID, workId.toString())
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(com.tottodrillo.R.string.notif_download_progress))
            .setContentText(romTitle)
            .setSmallIcon(com.tottodrillo.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                appContext.getString(com.tottodrillo.R.string.notif_cancel_download),
                cancelPendingIntent
            )
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ richiede un tipo esplicito per i Foreground Service
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Mostra notifica completamento
     */
    private fun showCompletedNotification(romTitle: String, filePath: String, romSlug: String?) {
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(com.tottodrillo.R.string.notif_download_completed))
            .setContentText(romTitle)
            .setSmallIcon(com.tottodrillo.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    /**
     * Mostra notifica errore
     */
    private fun showErrorNotification(romTitle: String, error: String, romSlug: String?) {
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(com.tottodrillo.R.string.notif_download_failed))
            .setContentText("$romTitle: $error")
            .setSmallIcon(com.tottodrillo.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    /**
     * Crea un PendingIntent per aprire l'app alla schermata della ROM
     */
    private fun createPendingIntent(romSlug: String?): PendingIntent {
        val intent = Intent(appContext, com.tottodrillo.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (romSlug != null) {
                putExtra("romSlug", romSlug)
                action = "OPEN_ROM_DETAIL"
            }
        }
        
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
