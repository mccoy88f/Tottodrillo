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

    // Client HTTP dedicato per il worker
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
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
                Log.d("DownloadWorker", "⚠️ File già esistente, eliminazione: ${outputFile.absolutePath}")
                outputFile.delete()
            }
            
            downloadFile(url, outputFile, romTitle, romSlug)

            // Crea/aggiorna file .status per confermare il download completato
            // Formato multi-riga: una riga per ogni URL scaricato
            // Ogni riga: <URL> o <URL>\t<PATH_ESTRAZIONE>
            try {
                val statusFile = File(targetPath, "$fileName.status")
                
                // Leggi le righe esistenti (se il file esiste)
                val existingLines = if (statusFile.exists()) {
                    statusFile.readLines().filter { it.isNotBlank() }
                } else {
                    emptyList()
                }
                
                // Verifica se l'URL esiste già nelle righe
                val urlExists = existingLines.any { line ->
                    val existingUrl = if (line.contains('\t')) {
                        line.substringBefore('\t')
                    } else {
                        line.trim()
                    }
                    existingUrl == url
                }
                
                if (!urlExists) {
                    // Aggiungi una nuova riga per questo URL
                    val newLine = url
                    val updatedContent = if (existingLines.isNotEmpty()) {
                        (existingLines + newLine).joinToString("\n")
                    } else {
                        newLine
                    }
                    statusFile.writeText(updatedContent)
                    Log.d("DownloadWorker", "✅ URL aggiunto al file .status: ${statusFile.absolutePath} -> $url")
                } else {
                    Log.d("DownloadWorker", "ℹ️ URL già presente nel file .status, nessuna modifica: $url")
                }
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
    private suspend fun downloadFile(url: String, outputFile: File, romTitle: String, romSlug: String?) {
        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download fallito: ${response.code}")
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
            .setContentTitle("Download in corso")
            .setContentText(romTitle)
            .setSmallIcon(com.tottodrillo.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Interrompi download",
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
            .setContentTitle("Download completato")
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
            .setContentTitle("Download fallito")
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
